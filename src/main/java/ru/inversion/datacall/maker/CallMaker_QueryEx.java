package ru.inversion.datacall.maker;

/**
 * Extended query preparation uses the same explicit parameter-handler contract
 * as the regular query maker. The former implementation depended on a removed
 * parameter list in {@code CallPrepareContext} and failed on the first replaced
 * literal.
 */
public class CallMaker_QueryEx extends CallMaker_Query {
}
