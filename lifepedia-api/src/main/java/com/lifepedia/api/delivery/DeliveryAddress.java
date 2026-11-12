package com.lifepedia.api.delivery;

import com.lifepedia.api.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/** Endereço de entrega salvo pela pessoa. */
@Entity
@Table(name = "delivery_addresses")
public class DeliveryAddress extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 40)
    private String label;

    @Column(length = 120)
    private String recipient;

    @Column(nullable = false, length = 8)
    private String cep;

    @Column(nullable = false, length = 160)
    private String street;

    @Column(length = 20)
    private String number;

    @Column(length = 80)
    private String complement;

    @Column(length = 80)
    private String district;

    @Column(nullable = false, length = 80)
    private String city;

    @Column(nullable = false, length = 2)
    private String state;

    @Column(name = "is_default", nullable = false)
    private boolean defaultAddress;

    protected DeliveryAddress() {
    }

    public DeliveryAddress(UUID userId) {
        this.userId = userId;
    }

    public void set(String label, String recipient, String cep, String street, String number, String complement,
                    String district, String city, String state) {
        this.label = label;
        this.recipient = recipient;
        this.cep = cep;
        this.street = street;
        this.number = number;
        this.complement = complement;
        this.district = district;
        this.city = city;
        this.state = state;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getLabel() {
        return label;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getCep() {
        return cep;
    }

    public String getStreet() {
        return street;
    }

    public String getNumber() {
        return number;
    }

    public String getComplement() {
        return complement;
    }

    public String getDistrict() {
        return district;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }

    public boolean isDefaultAddress() {
        return defaultAddress;
    }

    public void setDefaultAddress(boolean defaultAddress) {
        this.defaultAddress = defaultAddress;
    }
}
