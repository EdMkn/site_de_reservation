package com.emakon.reservation.domain;

import com.emakon.reservation.availability.Modality;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Rendez-vous (table rendez_vous). Jamais supprimé : une annulation change le statut.
 *
 * Pas de setters sur les champs qui définissent le rendez-vous (qui, quand, où) :
 * ils sont fixés une fois pour toutes par le constructeur.
 */
@Entity
@Table(name = "rendez_vous")
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false, updatable = false)
    private Patient patient;

    // En base, (motif_id, praticien_id) forme une clé étrangère composée vers motif.
    // JPA n'a pas besoin de la connaître : deux @ManyToOne simples suffisent, et le
    // constructeur garantit la cohérence en déduisant le praticien du motif.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "praticien_id", nullable = false, updatable = false)
    private Practitioner practitioner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "motif_id", nullable = false, updatable = false)
    private ConsultationType consultationType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "modalite", nullable = false, updatable = false, columnDefinition = "modalite")
    private Modality modality;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lieu_id", updatable = false)
    private Location location;

    @Column(name = "debut", nullable = false, updatable = false)
    private Instant start;

    // Copiée à la réservation : si le praticien change ensuite la durée du motif,
    // ce rendez-vous ne bouge pas (et la contrainte d'exclusion reste exacte).
    @Column(name = "fin", nullable = false, updatable = false)
    private Instant end;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "statut", nullable = false, columnDefinition = "statut_rdv")
    private AppointmentStatus status;

    @Column(name = "note_patient", columnDefinition = "text")
    private String patientNote;

    @Generated
    @Column(name = "cree_le", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "annule_le")
    private Instant cancelledAt;

    protected Appointment() {
    }

    /**
     * Nouveau rendez-vous confirmé. Le praticien est celui du motif, et la fin vaut
     * début + durée du motif : deux règles du modèle garanties par construction.
     *
     * @param location null en distanciel
     */
    public Appointment(Patient patient, ConsultationType consultationType, Modality modality,
                       Location location, Instant start, String patientNote) {
        this.patient = patient;
        this.consultationType = consultationType;
        this.practitioner = consultationType.getPractitioner();
        this.modality = modality;
        this.location = location;
        this.start = start;
        this.end = start.plus(consultationType.getDuration());
        this.status = AppointmentStatus.CONFIRME;
        this.patientNote = patientNote;
    }

    public Long getId() {
        return id;
    }

    public Patient getPatient() {
        return patient;
    }

    public Practitioner getPractitioner() {
        return practitioner;
    }

    public ConsultationType getConsultationType() {
        return consultationType;
    }

    public Modality getModality() {
        return modality;
    }

    public Location getLocation() {
        return location;
    }

    public Instant getStart() {
        return start;
    }

    public Instant getEnd() {
        return end;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public String getPatientNote() {
        return patientNote;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }
}
