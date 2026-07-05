//package org.asvosonk.session.infrastructure.persistence.converter;
//
//import jakarta.persistence.AttributeConverter;
//import jakarta.persistence.Converter;
//import org.asvosonk.session.domain.valueobject.AttendanceStatus;
//
///**
// * JPA AttributeConverter that maps AttendanceStatus Java enum values
// * to PostgreSQL attendance_status ENUM values.
// *
// * The Java enum uses 'default_status' (because 'default' is a Java
// * reserved keyword), but the PostgreSQL ENUM stores 'default'.
// */
//@Converter(autoApply = true)
//public class AttendanceStatusConverter implements AttributeConverter<AttendanceStatus, String> {
//
//    @Override
//    public String convertToDatabaseColumn(AttendanceStatus attribute) {
//        if (attribute == null) return null;
//        return switch (attribute) {
//            case up_to_date      -> "up_to_date";
//            case covered_by_fund -> "covered_by_fund";
//            case default_status  -> "default";
//            case recovered       -> "recovered";
//        };
//    }
//
//    @Override
//    public AttendanceStatus convertToEntityAttribute(String dbData) {
//        if (dbData == null) return null;
//        return switch (dbData) {
//            case "up_to_date"      -> AttendanceStatus.up_to_date;
//            case "covered_by_fund" -> AttendanceStatus.covered_by_fund;
//            case "default"         -> AttendanceStatus.default_status;
//            case "recovered"       -> AttendanceStatus.recovered;
//            default -> throw new IllegalArgumentException(
//                "Unknown AttendanceStatus value in DB: " + dbData);
//        };
//    }
//}
