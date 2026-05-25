package com.ankur.design.lld.doctor.model;

/**
 * Immutable appointment request from a patient.
 */
public class    AppointmentRequest {

    private final String patientId;
    private final String doctorId;
    private final String slotId;

    public AppointmentRequest(String patientId, String doctorId, String slotId) {
        this.patientId = patientId;
        this.doctorId  = doctorId;
        this.slotId    = slotId;
    }

    public String getPatientId() { return patientId; }
    public String getDoctorId()  { return doctorId; }
    public String getSlotId()    { return slotId; }

    @Override
    public String toString() {
        return "AppointmentRequest{patient='" + patientId +
               "', doctor='" + doctorId + "', slot='" + slotId + "'}";
    }
}