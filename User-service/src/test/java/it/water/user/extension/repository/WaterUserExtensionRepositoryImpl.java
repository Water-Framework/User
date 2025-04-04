package it.water.user.extension.repository;

import it.water.core.interceptors.annotations.FrameworkComponent;
import it.water.repository.jpa.WaterJpaRepositoryImpl;
import it.water.user.extension.api.WaterUserExtensionRepository;
import it.water.user.extension.entity.WaterUserExtension;

@FrameworkComponent
public class WaterUserExtensionRepositoryImpl extends WaterJpaRepositoryImpl<WaterUserExtension> implements WaterUserExtensionRepository {
    public WaterUserExtensionRepositoryImpl() {
        super(WaterUserExtension.class, "water-default-persistence-unit");
    }
}
