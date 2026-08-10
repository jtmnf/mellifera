package com.joaonf.mellifera.bee;

import java.util.List;

/// What one input turns into when it is spun down, and how long that takes.
///
/// Every output is independently rolled -- Forestry's centrifuge takes a map of
/// stack -> chance, so a Frozen comb can hand back wax, a honey drop, a snowball and a
/// pollen cluster all from the same spin, or only some of them.
public record CentrifugeRecipe(int processTicks, List<ItemProduct> outputs) {}
