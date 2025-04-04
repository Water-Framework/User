package it.water.user.extension.entity;

import it.water.repository.jpa.model.AbstractJpaEntityExpansion;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class WaterUserExtension extends AbstractJpaEntityExpansion {
    private String extensionField1;
    private String extensionField2;
}
