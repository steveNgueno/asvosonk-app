package org.asvosonk.session.domain.valueobject;

public enum AttendanceStatus {
    up_to_date,     // membre à jour
    covered_by_fund,// cotisé par le fond de roulement
    default_status, // échec (mapped to DB value 'default' via @Column)
    recovered       // échec ou avance recouverts rétroactivement
}
