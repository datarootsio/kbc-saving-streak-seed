package io.dataroots.savingstreak.points;

/**
 * Why a batch of points was earned. Base accrual is the only way to earn any, so far.
 *
 * <p>Public, unlike the batch it is written on: what a deposit earned is reported broken down by
 * reason, so the reasons are part of what this module says rather than part of how it stores
 * things. A caller still cannot learn that points are kept as dated batches — only that a figure it
 * was handed was earned for this reason and not that one.
 */
public enum PointsReason {
    BASE_ACCRUAL
}
