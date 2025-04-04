package it.water.user.extension;

import it.water.core.api.model.BaseEntity;
import it.water.core.api.service.EntityExtensionService;
import it.water.core.interceptors.annotations.FrameworkComponent;
import it.water.user.extension.entity.WaterUserExtension;
import it.water.user.model.WaterUser;
import lombok.Getter;
import lombok.Setter;

@FrameworkComponent(properties = EntityExtensionService.RELATED_ENTITY_PROPERTY+"=it.water.user.model.WaterUser")
public class WaterUserExtensionService implements EntityExtensionService {
    @Setter
    @Getter
    private String waterEntityExtensionType;

    @Override
    public Class<? extends BaseEntity> relatedType() {
        return WaterUser.class;
    }

    @Override
    public Class<? extends BaseEntity> type() {
        return WaterUserExtension.class;
    }
}
