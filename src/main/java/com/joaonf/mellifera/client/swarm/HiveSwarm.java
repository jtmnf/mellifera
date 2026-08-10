package com.joaonf.mellifera.client.swarm;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.state.BeeRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/// The foragers working out of one hive: a handful of bees, each on a trip of its own.
///
/// This is the simulation half of what used to be a particle. It moved off particles because a
/// particle is a camera-facing quad and nothing else -- the whole rendering path takes textured
/// quads, so a particle could never be the recoloured vanilla bee the rest of the mod uses for
/// its items. The trip logic below is unchanged from that particle; only where it lives and
/// what draws it are new (see HiveBeeRenderer).
///
/// The population is a fraction of what it was, and deliberately: the old flat sprite was a
/// single quad, so a couple of dozen per hive cost nothing, whereas each bee here is Mojang's
/// bee model, posed and submitted individually. MAX_BEES is what a hive can show without ever
/// becoming an entity crowd -- and a handful of legible bees reads better than a cloud of specks
/// anyway, because you can now actually watch one of them work a flower.
///
/// A honeybee's trip has four recognisable parts, and all four are here:
///
///   1. An orientation flight, for a minority of departures -- the widening arcs a bee flies
///      facing the entrance before it commits to a direction. It is the single most recognisable
///      thing that happens at a real hive entrance.
///   2. A beeline out to a flower that genuinely exists in the world, found by probing the
///      terrain around the hive. Bees do not search on the wing at random; a forager leaves
///      already aimed, which is why the outbound leg is straight and fast.
///   3. Working a *patch*: one flower, then short hops to its neighbours within a few blocks.
///      Real foragers show flower constancy and patch fidelity, so hops search a small radius
///      around the last flower and the trip ends when that search comes up empty.
///   4. A straight run home once the load is full. Foragers return directly; the word for that
///      line is where "beeline" comes from.
///
/// What is deliberately *not* modelled is any kind of trail. Honeybees leave no path to follow --
/// they recruit by dance inside the hive, and the only scent they leave on a flower is a
/// short-lived mark meaning "already visited". There is nothing to draw between hive and flower.
///
/// A bee does not go through the world, and does not go under it. Three separate rules hold that
/// up, because no one of them is enough on its own: bees collide with anything that has a
/// collision shape and slide along it (move); they are held above the ground so that rising
/// terrain is climbed rather than run into (followTerrain); and they only pick flowers growing on
/// top of the ground, preferring ones they can see from where they stand (flowerInColumn,
/// findFlower). A bee that still ends up getting nowhere gives up and goes home rather than
/// grinding against whatever stopped it.
///
/// Everything is read client-side off blocks already in memory: the hive still syncs nothing but
/// whether it is working, its species colour and its territory.
public final class HiveSwarm {
    /// Bees in the air at once, per hive. See the class note: this is the number the 3D model
    /// pays for, not the number the old sprite could afford.
    private static final int MAX_BEES = 5;

    /// Ticks between departures. Roughly one bee leaving every four seconds, which with trips of
    /// ten-odd seconds settles at the cap without ever launching a visible burst.
    private static final int SPAWN_INTERVAL_MIN = 60;
    private static final int SPAWN_INTERVAL_MAX = 110;

    /// Faces a bee may leave by. Down is excluded: a hive standing on the ground would push half
    /// of its bees straight into the block below, where they are never seen.
    private static final Direction[] FLIGHT_FACES = {
        Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP
    };

    /// Distance from block centre to the face the bee departs from, and how far the jitter along
    /// the other two axes may wander -- both just inside the block's own 0.5 half-width so bees
    /// leave from the hive's skin rather than from five fixed points.
    private static final double FACE_OFFSET = 0.55;
    private static final double FACE_JITTER = 0.7;


    /// Ticks a swarm may run in one frame to catch up with the clock.
    ///
    /// The swarm is driven from the renderer's extract pass, so it only advances while its hive
    /// is on screen -- look away for a minute and it owes a minute of ticks. Paying more than a
    /// few would make a hive lurch as it came into view; instead the bees simply carry on from
    /// where they were, which nobody can tell apart from having flown the whole time.
    private static final int MAX_CATCHUP_TICKS = 4;

    private final BlockPos hive;
    private final RandomSource random = RandomSource.create();
    private final List<Forager> bees = new ArrayList<>();

    private int spawnCooldown;
    private long lastTick;

    /// Last game tick this swarm was extracted for rendering. The renderer keeps swarms in a map
    /// and has no callback for a hive going away, so this is what tells it which entries have
    /// been abandoned.
    private long lastSeen;

    public HiveSwarm(BlockPos hive, long gameTime) {
        this.hive = hive;
        this.lastTick = gameTime;
        this.lastSeen = gameTime;
    }

    public List<Forager> bees() {
        return this.bees;
    }

    public boolean isEmpty() {
        return this.bees.isEmpty();
    }

    public long lastSeen() {
        return this.lastSeen;
    }

    /// Brings the swarm up to `gameTime`, running whole ticks. Several frames inside one tick
    /// advance nothing -- the motion between them comes from interpolating the tick that has
    /// already happened (see Forager.extract), not from simulating faster.
    public void advance(ClientLevel level, long gameTime, int territory, boolean departing) {
        long elapsed = gameTime - this.lastTick;
        this.lastTick = gameTime;
        this.lastSeen = gameTime;

        int steps = (int) Math.min(Math.max(elapsed, 0L), MAX_CATCHUP_TICKS);
        for (int step = 0; step < steps; step++) {
            this.tick(level, territory, departing);
        }
    }

    /// One tick of the whole swarm.
    ///
    /// `departing` gates only the launching of new bees, not the flying of existing ones: a hive
    /// whose queen has just died stops sending bees out but the ones already in the field still
    /// finish their trip and go home, which is both what happens and what stops five bees
    /// vanishing in mid-air the moment a cycle ends.
    private void tick(ClientLevel level, int territory, boolean departing) {
        for (int i = this.bees.size() - 1; i >= 0; i--) {
            if (!this.bees.get(i).tick(level)) {
                this.bees.remove(i);
            }
        }

        if (!departing || --this.spawnCooldown > 0) {
            return;
        }

        this.spawnCooldown = Mth.nextInt(this.random, SPAWN_INTERVAL_MIN, SPAWN_INTERVAL_MAX);
        if (this.bees.size() >= MAX_BEES) {
            return;
        }

        Forager bee = this.launch(level, territory);
        if (bee != null) {
            this.bees.add(bee);
        }
    }

    /// Puts a bee on a face of the hive, aimed away from it.
    ///
    /// On the axis of the chosen face the bee sits on that face; on the other two it is jittered
    /// across it. The face direction seeds both the flower search and the initial velocity, so a
    /// bee leaving the north side sets off north instead of doubling back through the block it
    /// just came out of.
    private @Nullable Forager launch(ClientLevel level, int territory) {
        Vec3i face = this.departureFace(level);
        if (face == null) {
            return null;
        }

        double x = this.hive.getX() + 0.5 + spread(this.random, face.getX());
        double y = this.hive.getY() + 0.5 + spread(this.random, face.getY());
        double z = this.hive.getZ() + 0.5 + spread(this.random, face.getZ());
        return new Forager(level, this.hive, this.random, x, y, z, face.getX(), face.getY(), face.getZ(), territory);
    }

    private static double spread(RandomSource random, int component) {
        return component != 0 ? component * FACE_OFFSET : (random.nextDouble() - 0.5) * FACE_JITTER;
    }

    /// A face a bee can actually get out through, or null if there is none.
    ///
    /// Every face is tried, from a random one, rather than a few random guesses: with only five
    /// candidates the exhaustive answer costs the same and is the difference between an apiary
    /// walled in on four sides using its one open face and using it a fifth of the time.
    ///
    /// No open face at all means no bee. The alternative is worse than nothing -- with the hive
    /// itself passable and everything else not, a bee launched into a wall would materialise
    /// inside the stone and buzz there until it gave up. A hive bricked into a wall showing no
    /// bees is not a bug to fix; it is the honest picture.
    private @Nullable Vec3i departureFace(ClientLevel level) {
        int start = this.random.nextInt(FLIGHT_FACES.length);
        for (int i = 0; i < FLIGHT_FACES.length; i++) {
            Vec3i face = FLIGHT_FACES[(start + i) % FLIGHT_FACES.length].getUnitVec3i();
            BlockPos outside = this.hive.offset(face);
            if (level.getBlockState(outside).getCollisionShape(level, outside).isEmpty()) {
                return face;
            }
        }

        return null;
    }

    /// One bee, its trip, and the pose the renderer draws it in.
    public static final class Forager {
        /// Blocks per tick. A real forager cruises at 6-7 m/s, which here would be ~0.3 and
        /// crosses a small territory in under a second -- too fast to follow with your eye, and
        /// it reads as a thrown item. These are the fastest speeds that still read as an insect.
        /// Home is quicker than out: a laden bee flies a purposeful straight line. Hops between
        /// neighbouring flowers are slower only because they are short -- a bee flits across a
        /// metre of meadow in a second or so, it does not commute.
        private static final double OUTBOUND_SPEED = 0.20;
        private static final double HOMEBOUND_SPEED = 0.24;
        private static final double HOP_SPEED = 0.12;
        private static final double ORIENT_SPEED = 0.09;
        private static final double HOVER_SPEED = 0.03;

        /// How sharply velocity turns toward the current target, as a fraction per tick. Bees
        /// change heading in a few wingbeats, not gradually, but snapping the vector outright
        /// makes the model teleport between straight lines; a third per tick settles in ~5 ticks.
        private static final double STEER = 0.3;

        /// How fast the body turns to face where it is going. Slower than STEER on purpose: the
        /// bee banks into a new heading rather than pivoting on the spot, and a model that snaps
        /// round its own axis is the one thing that would give away that this is not an entity.
        private static final float TURN_RATE = 0.35F;

        /// Below this the heading is meaningless (a bee holding station over a flower has almost
        /// no velocity to point along), so the last heading is kept instead of being recomputed
        /// from noise.
        private static final double HEADING_EPSILON = 1.0E-3;

        /// Inside this distance the bee eases off, down to a floor so it still closes the last
        /// few centimetres. Without it a bee arrives at a flower at full cruise and shoots past
        /// it -- which is what a hoverfly does and a bee does not.
        private static final double BRAKING_DISTANCE = 1.5;
        private static final double MIN_SPEED_FRACTION = 0.15;

        /// Close enough to count as arrived, in three dimensions: these bees descend to flowers
        /// on the ground and climb back to hive height, so height is part of "there".
        private static final double ARRIVAL = 0.25;

        /// How far above the ground an invented waypoint is placed -- high enough to be over the
        /// grass, low enough that a scouting bee is still working the field rather than
        /// overflying it.
        private static final double TARGET_CLEARANCE = 1.0;

        /// Radius around the hive where the terrain rule is switched off. See followTerrain.
        private static final double HIVE_CLEARANCE = 1.5;

        /// How far over the ground the terrain rule holds a bee, so that the model does not sink
        /// into it. See followTerrain.
        private static final double GROUND_CLEARANCE = 0.2;

        /// Consecutive ticks of getting nowhere before a bee gives up and goes home. Between
        /// collision, the terrain rule and only ever aiming at somewhere it can see, a bee should
        /// not end up pinned -- but "should not" is not "cannot", and a bee grinding against a
        /// wall for the rest of its trip is the one failure that would be unmissable.
        private static final int STALL_TICKS = 40;

        /// Multiples of that after which the bee is dropped outright rather than sent home.
        private static final int STALL_GIVE_UP = 3;

        /// A small high-frequency wobble across the line of flight. Much smaller than the flat
        /// sprite needed, because the model now beats its own wings and bobs its own abdomen
        /// (see BeeModel.setupAnim) -- all this has to add is that a bee never holds a perfectly
        /// straight line.
        private static final double BUZZ_RATE = 0.9;
        private static final double BUZZ_SWAY = 0.008;
        private static final double BUZZ_HOVER_GAIN = 2.0;

        /// Fraction of departures that begin with an orientation flight, and how many widening
        /// arcs it runs. Kept a minority: in a real colony it is mostly the young and the newly
        /// relocated that orient, and if every bee did it the hive would look like it was
        /// swarming.
        private static final float ORIENTATION_CHANCE = 0.15F;
        private static final int ORIENT_LEGS = 3;
        private static final double ORIENT_RADIUS = 1.6;

        /// Probes for the first flower (anywhere in territory) and for each hop (around the last
        /// flower). Each probe is one column, so the cost is bounded by probes x (SEARCH_ABOVE +
        /// SEARCH_BELOW) block lookups against chunks the client already has in memory.
        private static final int SEARCH_PROBES = 10;
        private static final int PATCH_PROBES = 6;

        /// How far a hop may look for the next flower. Vanilla's pollinate goal uses 5 for its
        /// initial search; a hop is tighter on purpose, because the point of it is that the bee
        /// stays in one patch instead of crossing the field.
        private static final double PATCH_RADIUS = 3.5;

        /// How far above and below the hive the ground may be and still count as this hive's
        /// own ground -- for deciding both which columns hold flowers worth flying to and how
        /// high the terrain under the bee is (see flowerInColumn, terrainTop). "The surface" a
        /// metre from a hive on a hillside is not the hive's own height, but a cliff top three
        /// times the hive's height away is not its meadow either.
        private static final int SEARCH_ABOVE = 3;
        private static final int SEARCH_BELOW = 4;

        /// Flowers worked per trip, and ticks spent on each. A real forager visits dozens to
        /// hundreds of florets before its crop is full; a bee that did that would still be out
        /// when its replacements had launched, so this is scaled to the handful in the air.
        private static final int VISITS_MIN = 1;
        private static final int VISITS_MAX = 3;
        private static final int DWELL_MIN = 15;
        private static final int DWELL_MAX = 40;

        /// Where the bee sits relative to the flower block, and how it shuffles about on it.
        /// Both mirror vanilla's BeePollinateGoal (HOVER_HEIGHT_WITHIN_FLOWER, HOVER_POS_OFFSET,
        /// POSITION_CHANGE_CHANCE), so a Mellifera bee working a poppy looks like a vanilla bee
        /// working the same poppy.
        private static final double HOVER_HEIGHT = 0.6;
        private static final double HOVER_OFFSET = 0.33;
        private static final int HOVER_SHIFT_CHANCE = 25;

        /// Legs of a scouting flight, flown when the territory turns up no flowers at all.
        /// Scouts are real -- a colony always has some out looking rather than working -- so an
        /// apiary in a desert still shows bees, they just come back empty.
        private static final int SCOUT_LEGS_MIN = 2;
        private static final int SCOUT_LEGS_MAX = 4;

        /// Lifetime is a safety net, not the plan: a bee normally ends its own trip by reaching
        /// the hive, and turns for home early enough to get there (see reserveForHomeLeg). The
        /// budget is two crossings of the territory plus the working time, bounded so a 1-block
        /// territory does not produce bees that expire on the doorstep.
        private static final int LIFETIME_MARGIN = 60;
        private static final int LIFETIME_MIN = 160;
        private static final int LIFETIME_MAX = 700;

        private enum Phase {
            /// Arcs around the entrance, before any direction is committed to.
            ORIENTING,
            /// Flying to a flower, to the next flower in the patch, or to a scouting waypoint.
            OUTBOUND,
            /// Holding station on a flower.
            WORKING,
            /// Straight line back to the hive; ends the trip on arrival.
            HOMEBOUND
        }

        private final RandomSource random;

        /// The hive block itself -- the one thing in the world a bee may be inside of.
        private final BlockPos hive;

        /// The point on the hive's skin this bee left by. The return leg aims at it, the flower
        /// search is centred on it, and the vertical search band is measured from it.
        private final double hiveX;
        private final double hiveY;
        private final double hiveZ;

        /// The queen's territory: how far this bee may forage, and nothing else. It is not a
        /// leash -- the bee has no reason to leave it, because that is where it looked.
        private final double range;

        /// Per-bee phase, so a burst of bees does not beat its wings in unison.
        private final float wingPhase;

        /// Vanilla's own render state, one per bee: the deferred renderer calls
        /// `model.setupAnim(state)` at draw time with whatever state was submitted, so a shared
        /// instance would pose every bee in the swarm identically (see
        /// ModelFeatureRenderer.Submit).
        private final BeeRenderState renderState = new BeeRenderState();

        private Phase phase;
        private double x;
        private double y;
        private double z;
        private double prevX;
        private double prevY;
        private double prevZ;
        private double xd;
        private double yd;
        private double zd;

        private float yaw;
        private float pitch;
        private float prevYaw;
        private float prevPitch;

        /// This frame's interpolated pose and light. Written by extract, read by submit.
        private float renderX;
        private float renderY;
        private float renderZ;
        private float renderYaw;
        private float renderPitch;
        private int lightCoords;

        private double targetX;
        private double targetY;
        private double targetZ;

        /// Cruise speed of the current leg. Set by every aim, because what the leg is determines
        /// how fast it is flown.
        private double speed;

        /// The flower being worked or flown to. Null on a scouting flight and on the way home.
        private @Nullable BlockPos flower;

        private int visitsLeft;
        private int dwell;
        private int legs;
        private int age;
        private int stallTicks;
        private final int lifetime;

        private Forager(
            ClientLevel level, BlockPos hive, RandomSource random,
            double x, double y, double z, double dirX, double dirY, double dirZ, int territory
        ) {
            this.random = random;
            this.hive = hive;
            this.x = this.prevX = this.hiveX = x;
            this.y = this.prevY = this.hiveY = y;
            this.z = this.prevZ = this.hiveZ = z;
            this.range = territory + 0.5;
            this.wingPhase = random.nextFloat() * Mth.TWO_PI;
            this.visitsLeft = Mth.nextInt(random, VISITS_MIN, VISITS_MAX);
            this.lifetime = this.budget();

            // Workers, not queens: no gilded antennae, and a stinger like every forager has.
            this.renderState.hasStinger = true;
            this.renderState.isOnGround = false;

            this.flower = this.findFlower(level, this.hiveX, this.hiveZ, this.range, SEARCH_PROBES, dirX, dirZ);
            this.xd = dirX * OUTBOUND_SPEED;
            this.yd = dirY * OUTBOUND_SPEED;
            this.zd = dirZ * OUTBOUND_SPEED;
            this.yaw = this.prevYaw = headingOf(this.xd, this.zd, 0.0F);

            if (random.nextFloat() < ORIENTATION_CHANCE) {
                this.legs = ORIENT_LEGS;
                this.phase = Phase.ORIENTING;
                this.aimAtArc(level);
            } else {
                this.depart(level);
            }
        }

        /// One tick of one bee. False means the trip is over and the bee should be dropped.
        private boolean tick(ClientLevel level) {
            this.prevX = this.x;
            this.prevY = this.y;
            this.prevZ = this.z;
            this.prevYaw = this.yaw;
            this.prevPitch = this.pitch;

            // A bee that cannot move at all is one the world has closed around -- a block placed
            // on top of it, most likely. Turning for home will not help; there is nowhere to turn
            // to. Ending the trip is the only thing that guarantees nobody is left looking at a
            // bee embedded in a block.
            if (this.age++ >= this.lifetime || this.stallTicks > STALL_TICKS * STALL_GIVE_UP) {
                return false;
            }

            // Turn for home while there is still time to get there. A bee that expires mid-air
            // pops out of existence in front of the player; one that runs its trip down to the
            // hive simply goes inside, which is what the HOMEBOUND arrival below is.
            //
            // A bee that has been getting nowhere for two seconds gives up on the same terms:
            // whatever it thought it was flying to, it is not getting there.
            if (this.phase != Phase.HOMEBOUND
                && (this.stallTicks > STALL_TICKS || this.age >= this.lifetime - this.reserveForHomeLeg())) {
                this.goHome();
            }

            switch (this.phase) {
                case ORIENTING -> {
                    if (this.reached()) {
                        if (--this.legs > 0) {
                            this.aimAtArc(level);
                        } else {
                            this.depart(level);
                        }
                    }
                }
                case OUTBOUND -> {
                    if (this.reached()) {
                        if (this.flower != null) {
                            this.beginVisit();
                        } else if (--this.legs > 0) {
                            this.aimAtScoutingPoint(level);
                        } else {
                            this.goHome();
                        }
                    }
                }
                case WORKING -> this.work(level);
                case HOMEBOUND -> {
                    if (this.reached()) {
                        // Home. Not "expired" -- gone inside.
                        return false;
                    }
                }
            }

            this.steer();
            this.move(level);
            this.face();
            return true;
        }

        // -- trip -----------------------------------------------------------------------------

        /// Leaves the hive: for the flower found at launch, or on a scouting flight if there was
        /// none.
        private void depart(ClientLevel level) {
            this.phase = Phase.OUTBOUND;
            if (this.flower != null) {
                this.aimAtFlower(OUTBOUND_SPEED);
            } else {
                this.legs = Mth.nextInt(this.random, SCOUT_LEGS_MIN, SCOUT_LEGS_MAX);
                this.aimAtScoutingPoint(level);
            }
        }

        private void beginVisit() {
            this.phase = Phase.WORKING;
            this.dwell = Mth.nextInt(this.random, DWELL_MIN, DWELL_MAX);
            this.aimAtFlower(HOVER_SPEED);
        }

        /// Holding station on a flower: mostly still, shuffling to another spot on the same block
        /// now and then, exactly as vanilla's pollinate goal does.
        private void work(ClientLevel level) {
            if (--this.dwell <= 0) {
                this.leaveFlower(level);
                return;
            }

            if (this.random.nextInt(HOVER_SHIFT_CHANCE) == 0) {
                this.aimAtFlower(HOVER_SPEED);
            }
        }

        /// Next flower in the same patch, or home. The patch search is what produces flower
        /// constancy: a bee that found a poppy in a meadow keeps working that meadow, and a bee
        /// on the one flower in a courtyard finds nothing next to it and leaves.
        private void leaveFlower(ClientLevel level) {
            if (--this.visitsLeft > 0 && this.flower != null) {
                BlockPos next = this.findFlower(
                    level, this.flower.getX() + 0.5, this.flower.getZ() + 0.5, PATCH_RADIUS, PATCH_PROBES, 0.0, 0.0);
                if (next != null && !next.equals(this.flower)) {
                    this.flower = next;
                    this.phase = Phase.OUTBOUND;
                    this.aimAtFlower(HOP_SPEED);
                    return;
                }
            }

            this.goHome();
        }

        /// Aims at the middle of the hive, not at the face the bee left by.
        ///
        /// The hive is the one block a bee may be inside of, so it can be flown *into* -- and a
        /// forager that disappears a moment after crossing the entrance has gone home, where one
        /// that stops flush against the outside wall and vanishes has been deleted. Same two
        /// lines of code, entirely different thing to watch.
        private void goHome() {
            this.phase = Phase.HOMEBOUND;
            this.flower = null;
            this.aim(this.hive.getX() + 0.5, this.hive.getY() + 0.5, this.hive.getZ() + 0.5, HOMEBOUND_SPEED);
        }

        /// Ticks to keep in hand for the run home from wherever the bee currently is. The margin
        /// covers the braking at both ends and the few ticks STEER needs to point the bee the
        /// right way.
        private int reserveForHomeLeg() {
            double dx = this.hiveX - this.x;
            double dy = this.hiveY - this.y;
            double dz = this.hiveZ - this.z;
            return (int) (Math.sqrt(dx * dx + dy * dy + dz * dz) / HOMEBOUND_SPEED) + LIFETIME_MARGIN;
        }

        /// Two crossings of the territory plus the working time, bounded at both ends.
        private int budget() {
            int travel = (int) (2.0 * this.range / OUTBOUND_SPEED);
            int working = VISITS_MAX * (DWELL_MAX + (int) (PATCH_RADIUS / HOP_SPEED));
            return Mth.clamp(travel + working + LIFETIME_MARGIN, LIFETIME_MIN, LIFETIME_MAX);
        }

        // -- flight ---------------------------------------------------------------------------

        private void aim(double x, double y, double z, double speed) {
            this.targetX = x;
            this.targetY = y;
            this.targetZ = z;
            this.speed = speed;
        }

        /// Hover position over the current flower: vanilla's `Vec3.atBottomCenterOf(pos).add(0,
        /// 0.6, 0)`, offset by up to a third of a block so repeated calls shuffle the bee about
        /// the flower instead of pinning it to the centre.
        private void aimAtFlower(double speed) {
            BlockPos pos = this.flower;
            if (pos == null) {
                this.goHome();
                return;
            }

            this.aim(
                pos.getX() + 0.5 + (this.random.nextDouble() * 2.0 - 1.0) * HOVER_OFFSET,
                pos.getY() + HOVER_HEIGHT,
                pos.getZ() + 0.5 + (this.random.nextDouble() * 2.0 - 1.0) * HOVER_OFFSET,
                speed);
        }

        /// A point on a widening circle around the entrance, at roughly hive height: the bee
        /// fixing where home is before it leaves.
        private void aimAtArc(ClientLevel level) {
            double angle = this.random.nextDouble() * Mth.TWO_PI;
            double radius = ORIENT_RADIUS * (ORIENT_LEGS - this.legs + 1);
            double x = this.hiveX + Math.cos(angle) * radius;
            double z = this.hiveZ + Math.sin(angle) * radius;
            this.aim(x, this.above(level, x, this.hiveY + (this.random.nextDouble() - 0.3) * ORIENT_RADIUS, z), z, ORIENT_SPEED);
        }

        /// A point somewhere in the territory, for a bee that found nothing to fly to. Drawn
        /// around the hive rather than around the bee, so a scout quarters the territory instead
        /// of random-walking out of it.
        private void aimAtScoutingPoint(ClientLevel level) {
            double angle = this.random.nextDouble() * Mth.TWO_PI;
            double radius = Math.sqrt(this.random.nextDouble()) * this.range;
            double x = this.hiveX + Math.cos(angle) * radius;
            double z = this.hiveZ + Math.sin(angle) * radius;
            this.aim(x, this.above(level, x, this.hiveY + (this.random.nextDouble() * 2.0 - 1.0), z), z, OUTBOUND_SPEED);
        }

        /// Lifts an invented waypoint clear of whatever the ground does out there.
        ///
        /// Only the arc and the scouting point need this. The other two targets are real places
        /// -- a flower block, and the face of the hive the bee left by -- and moving those would
        /// mean aiming somewhere the bee is not trying to go.
        private double above(ClientLevel level, double x, double y, double z) {
            return Math.max(y, this.terrainTop(level, x, z) + TARGET_CLEARANCE);
        }

        private boolean reached() {
            double dx = this.targetX - this.x;
            double dy = this.targetY - this.y;
            double dz = this.targetZ - this.z;
            return dx * dx + dy * dy + dz * dz < ARRIVAL * ARRIVAL;
        }

        /// Turns the velocity toward the current target at the current leg's speed, easing off on
        /// the last stretch. Speed is set by the leg, not by how far the target is: a bee
        /// crossing eight blocks flies eight blocks' worth of time, which is the difference
        /// between a bee that has somewhere to be and one being interpolated toward a point.
        private void steer() {
            double dx = this.targetX - this.x;
            double dy = this.targetY - this.y;
            double dz = this.targetZ - this.z;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance < 1.0E-4) {
                return;
            }

            double throttle = Math.max(Math.min(distance / BRAKING_DISTANCE, 1.0), MIN_SPEED_FRACTION);
            double scale = this.speed * throttle / distance;
            this.xd += (dx * scale - this.xd) * STEER;
            this.yd += (dy * scale - this.yd) * STEER;
            this.zd += (dz * scale - this.zd) * STEER;
        }

        /// The buzz rides on top of the flight vector rather than replacing it, so a bee still
        /// makes progress toward its flower while jittering.
        ///
        /// One axis at a time, as everything that collides in this game moves: a bee that meets a
        /// wall head-on keeps whatever sideways and vertical motion it had and slides along the
        /// wall, instead of stopping dead in front of it. There is still no gravity -- a bee
        /// flies.
        private void move(ClientLevel level) {
            double phase = this.age * BUZZ_RATE + this.wingPhase;
            double gain = this.phase == Phase.WORKING ? BUZZ_HOVER_GAIN : 1.0;
            double stepX = this.xd + Math.cos(phase) * BUZZ_SWAY * gain;
            double stepY = this.yd + Math.sin(phase * 0.5) * BUZZ_SWAY * gain;
            double stepZ = this.zd + Math.sin(phase) * BUZZ_SWAY * gain;

            boolean stuck = false;
            if (this.solid(level, this.x + stepX, this.y, this.z)) {
                this.xd = 0.0;
                stuck = true;
            } else {
                this.x += stepX;
            }

            if (this.solid(level, this.x, this.y + stepY, this.z)) {
                this.yd = 0.0;
                stuck = true;
            } else {
                this.y += stepY;
            }

            if (this.solid(level, this.x, this.y, this.z + stepZ)) {
                this.zd = 0.0;
                stuck = true;
            } else {
                this.z += stepZ;
            }

            this.stallTicks = stuck ? this.stallTicks + 1 : 0;
            this.followTerrain(level);
        }

        /// Keeps a bee over the ground rather than through it.
        ///
        /// Collision alone is not enough: a bee flying level at a rising slope would be stopped
        /// flat against it and buzz there until its trip ran out, because sliding needs somewhere
        /// to slide *to* and it has no upward motion to slide with. Lifting it to the surface is
        /// what makes it climb the hill instead -- which is also how a real forager crosses one,
        /// following the contour a metre up rather than navigating it.
        ///
        /// Not applied right at the hive, where the ground *is* the hive: a bee sitting on the
        /// side of an apiary is below the top of the block it just came out of, and pushing it up
        /// to clear that would park every departure and every arrival on the roof.
        private void followTerrain(ClientLevel level) {
            double dx = this.x - this.hiveX;
            double dz = this.z - this.hiveZ;
            if (dx * dx + dz * dz < HIVE_CLEARANCE * HIVE_CLEARANCE) {
                return;
            }

            // The bee is drawn centred on its position, so sitting exactly at the surface buries
            // half of it. The clearance is roughly the model's own half-height.
            double floor = this.terrainTop(level, this.x, this.z) + GROUND_CLEARANCE;
            if (this.y < floor && !this.solid(level, this.x, floor, this.z)) {
                this.y = floor;
                this.yd = Math.max(this.yd, 0.0);
            }
        }

        /// The height the ground reaches in this column, or far below everything where that
        /// height is not ground the bee is flying over.
        ///
        /// MOTION_BLOCKING_NO_LEAVES, not WORLD_SURFACE: grass and flowers do not stop a bee and
        /// must not push one up either, and a hive under a tree would otherwise have its bees
        /// shoved out through the canopy.
        ///
        /// The band is what keeps this honest indoors. A hive in a cellar or under a roof reads a
        /// "ground height" that is really the building on top of it, and anything that far above
        /// the hive is not terrain the bee is crossing -- so out there the rule simply does not
        /// apply, and collision is left to do the work on its own.
        private double terrainTop(ClientLevel level, double x, double z) {
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
            return ground > this.hiveY + SEARCH_ABOVE ? Double.NEGATIVE_INFINITY : ground;
        }

        /// Whether a point is inside something a bee cannot fly through.
        ///
        /// The hive is the one exception, and it is a total one -- the block simply is not there
        /// as far as its own bees are concerned. They live in it: a returning forager should
        /// disappear *into* the hive rather than stop flush against its side and wink out, and a
        /// departing one should be able to come out of the entrance rather than out of the air
        /// next to it.
        ///
        /// Everything else is solid, and any collision shape at all counts as the whole block.
        /// That is coarser than the truth for stairs and fences, and it is the right coarseness:
        /// a bee is a point to this simulation, and threading one through the gap under a fence
        /// gate is not a thing anyone will thank us for. It costs nothing where it matters,
        /// because flowers, crops and grass have no collision shape to begin with -- which is
        /// also what lets a bee settle onto the flower it is working.
        private boolean solid(ClientLevel level, double x, double y, double z) {
            BlockPos pos = BlockPos.containing(x, y, z);
            if (pos.equals(this.hive)) {
                return false;
            }

            return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
        }

        /// Whether the bee could fly straight from where it is to a point without going through
        /// anything.
        ///
        /// Asked when a flower is chosen, never while flying: one ray per candidate, a handful
        /// per trip. What it cannot answer is whether the bee could get there some *other* way
        /// than straight -- which it often can, by climbing -- so it is used as a preference and
        /// not a veto (see findFlower).
        private boolean reachable(ClientLevel level, double x, double y, double z) {
            return level.clip(new ClipContext(
                new Vec3(this.x, this.y, this.z),
                new Vec3(x, y, z),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                CollisionContext.empty())).getType() == HitResult.Type.MISS;
        }

        /// Points the body along the direction of travel, easing into turns.
        private void face() {
            double horizontal = Math.sqrt(this.xd * this.xd + this.zd * this.zd);
            if (horizontal < HEADING_EPSILON) {
                return;
            }

            this.yaw = Mth.rotLerp(TURN_RATE, this.yaw, headingOf(this.xd, this.zd, this.yaw));
            this.pitch = Mth.rotLerp(TURN_RATE, this.pitch, (float) -Math.toDegrees(Math.atan2(this.yd, horizontal)));
        }

        /// Yaw, in the frame the renderer draws in.
        ///
        /// The bee model faces its own -Z, and HiveBeeRenderer mirrors X and Y (the `scale(-s,
        /// -s, s)` every entity model is drawn under). Under that mirror a rotation of `a` about
        /// Y sends the model's nose to world `(sin a, -cos a)`, so matching a heading of
        /// `(dx, dz)` means `a = atan2(dx, -dz)` -- not the `atan2(-dx, dz)` an unmirrored frame
        /// would want.
        private static float headingOf(double dx, double dz, float fallback) {
            if (Math.abs(dx) < HEADING_EPSILON && Math.abs(dz) < HEADING_EPSILON) {
                return fallback;
            }
            return (float) Math.toDegrees(Math.atan2(dx, -dz));
        }

        // -- finding flowers ------------------------------------------------------------------

        /// Probes columns at random within `radius` of a point, returning a flower to fly to.
        ///
        /// Sampling rather than sweeping: a full sweep of a 32-block territory is 4000-odd columns
        /// for something that only has to look plausible, and a bee that flies to the nearest
        /// flower every time looks like a machine. `dirX`/`dirZ`, when given, bias the first half
        /// of the probes to that side of the hive.
        ///
        /// A clear line to the flower is preferred, not required. Requiring it would rule out
        /// half a meadow, because a bee cruising a foot off the ground has the next hummock
        /// between it and everything beyond -- and it crosses those perfectly well, by climbing
        /// (see followTerrain). What the preference actually buys is that a bee picks the poppy
        /// on this side of the garden wall over the one behind it whenever both are on offer.
        private @Nullable BlockPos findFlower(
            ClientLevel level, double centerX, double centerZ, double radius, int probes, double dirX, double dirZ
        ) {
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            boolean biased = dirX != 0.0 || dirZ != 0.0;
            BlockPos fallback = null;

            for (int probe = 0; probe < probes; probe++) {
                double angle = this.random.nextDouble() * Mth.TWO_PI;
                // sqrt, so points spread evenly over the disc instead of piling up at the hive.
                double distance = Math.sqrt(this.random.nextDouble()) * radius;
                double offsetX = Math.cos(angle) * distance;
                double offsetZ = Math.sin(angle) * distance;

                // Mirrored rather than discarded: folding the wrong half of the disc onto the
                // right one biases the search without throwing probes away, and the probes are
                // the only thing that decides whether this bee finds a flower at all.
                if (biased && probe * 2 < probes && offsetX * dirX + offsetZ * dirZ < 0.0) {
                    offsetX = -offsetX;
                    offsetZ = -offsetZ;
                }

                BlockPos found = this.flowerInColumn(
                    level, cursor, Mth.floor(centerX + offsetX), Mth.floor(centerZ + offsetZ));
                if (found == null) {
                    continue;
                }

                if (this.reachable(level, found.getX() + 0.5, found.getY() + HOVER_HEIGHT, found.getZ() + 0.5)) {
                    return found;
                }

                if (fallback == null) {
                    fallback = found;
                }
            }

            return fallback;
        }

        /// The flower growing on top of this column, if there is one.
        ///
        /// Two blocks are looked at, and only two: the one standing on the ground, and the one
        /// above it, which is where the upper half of a sunflower lives (and the half vanilla's
        /// own predicate insists on). Everything a bee should be interested in grows *on* the
        /// ground, so anything below that height is buried -- in a cave, under a floor, inside a
        /// hill -- and finding it there is precisely how a bee ends up flying underground to
        /// reach it.
        ///
        /// Columns whose ground is outside the hive's own height band are skipped entirely. A
        /// cliff top ten blocks up or a ravine floor twenty down is not this hive's meadow, and
        /// the bee would spend its whole trip climbing to it.
        private @Nullable BlockPos flowerInColumn(ClientLevel level, BlockPos.MutableBlockPos cursor, int x, int z) {
            int hiveLevel = Mth.floor(this.hiveY);
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (ground > hiveLevel + SEARCH_ABOVE || ground < hiveLevel - SEARCH_BELOW) {
                return null;
            }

            for (int y = Math.min(ground + 1, level.getMaxY()); y >= ground; y--) {
                cursor.set(x, y, z);
                BlockState state = level.getBlockState(cursor);
                if (attracts(state)) {
                    return cursor.immutable();
                }
            }

            return null;
        }

        /// What counts as worth flying to. `Bee.attractsBees` is vanilla's own answer (flowers,
        /// less the waterlogged ones and the lower half of a sunflower); BEE_GROWABLES adds the
        /// crops the apiary's flowering pass bonemeals, so a hive over a wheat field shows bees
        /// in the wheat.
        private static boolean attracts(BlockState state) {
            return Bee.attractsBees(state) || state.is(BlockTags.BEE_GROWABLES);
        }

        // -- rendering ------------------------------------------------------------------------

        /// This frame's pose, worked out once during the renderer's extract pass so that submit
        /// does no arithmetic and allocates nothing.
        ///
        /// Position is relative to the hive block's corner, which is where a block entity
        /// renderer's pose stack already stands. Everything is interpolated across the tick: the
        /// simulation runs at 20 Hz and the frame rate does not.
        void extract(ClientLevel level, BlockPos hive, float partialTick, int hiveLight) {
            this.renderX = (float) (Mth.lerp(partialTick, this.prevX, this.x) - hive.getX());
            this.renderY = (float) (Mth.lerp(partialTick, this.prevY, this.y) - hive.getY());
            this.renderZ = (float) (Mth.lerp(partialTick, this.prevZ, this.z) - hive.getZ());
            this.renderYaw = Mth.rotLerp(partialTick, this.prevYaw, this.yaw);
            this.renderPitch = Mth.rotLerp(partialTick, this.prevPitch, this.pitch);

            // Light where the bee actually is, not where its hive is: a bee out over a sunlit
            // meadow should not be shaded by the tree its hive stands under. The hive's own light
            // is the fallback for a bee somehow over an unloaded chunk, which is cheaper than
            // faulting a chunk in during a frame.
            BlockPos at = BlockPos.containing(this.x, this.y, this.z);
            this.lightCoords = level.hasChunkAt(at) ? LightCoordsUtil.getLightCoords(level, at) : hiveLight;

            // ageInTicks drives the wing beat, and carries the per-bee phase so a swarm does not
            // flap in unison.
            this.renderState.ageInTicks = this.age + partialTick + this.wingPhase;
        }

        public float renderX() {
            return this.renderX;
        }

        public float renderY() {
            return this.renderY;
        }

        public float renderZ() {
            return this.renderZ;
        }

        public float renderYaw() {
            return this.renderYaw;
        }

        public float renderPitch() {
            return this.renderPitch;
        }

        public int lightCoords() {
            return this.lightCoords;
        }

        public BeeRenderState renderState() {
            return this.renderState;
        }
    }
}
