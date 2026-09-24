package com.proveyu.booking.domain;

import com.proveyu.shared.error.SlotFullException;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "slot_inventory")
public class SlotInventory {

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "slot_id", columnDefinition = "CHAR(36)")
    private UUID slotId;

    @Column(name = "total_seats", nullable = false)
    private short totalSeats;

    @Column(name = "seats_available", nullable = false)
    private short seatsAvailable;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    public SlotInventory() {}

    public SlotInventory(UUID slotId, short totalSeats, short seatsAvailable, int version) {
        this.slotId = slotId;
        this.totalSeats = totalSeats;
        this.seatsAvailable = seatsAvailable;
        this.version = version;
    }

    public UUID getSlotId() { return slotId; }
    public void setSlotId(UUID slotId) { this.slotId = slotId; }

    public short getTotalSeats() { return totalSeats; }
    public void setTotalSeats(short totalSeats) { this.totalSeats = totalSeats; }

    public short getSeatsAvailable() { return seatsAvailable; }
    public void setSeatsAvailable(short seatsAvailable) { this.seatsAvailable = seatsAvailable; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public void reserveOneSeat() {
        if (seatsAvailable <= 0) {
            throw new SlotFullException();
        }
        seatsAvailable--;
    }

    public void releaseOneSeat() {
        if (seatsAvailable < totalSeats) {
            seatsAvailable++;
        }
    }
}
