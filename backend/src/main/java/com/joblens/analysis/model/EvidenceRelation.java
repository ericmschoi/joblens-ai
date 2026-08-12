package com.joblens.analysis.model;

/** Whether evidence matches the requirement itself or transfers to it from adjacent work. */
public enum EvidenceRelation {
    DIRECT,
    TRANSFERABLE,
    NONE
}
