package com.xatkit.dsl.entity.impl;

import com.xatkit.dsl.entity.CustomEntityDefinitionProvider;
import com.xatkit.dsl.entity.EntityDefinitionReferenceProvider;
import com.xatkit.intent.CustomEntityDefinition;
import com.xatkit.intent.CustomEntityDefinitionReference;
import com.xatkit.intent.EntityDefinitionReference;
import com.xatkit.intent.IntentFactory;
import lombok.NonNull;

public class CustomEntityDefinitionProviderImpl<T extends CustomEntityDefinition> implements
        CustomEntityDefinitionProvider, EntityDefinitionReferenceProvider {

    protected T entity;

    @Override
    public @NonNull T getEntity() {
        return this.entity;
    }

    public @NonNull CustomEntityDefinitionProviderImpl<T> name(@NonNull String name) {
        this.entity.setName(name);
        return this;
    }

    @Override
    public @NonNull EntityDefinitionReference getEntityReference() {
        CustomEntityDefinitionReference reference = IntentFactory.eINSTANCE.createCustomEntityDefinitionReference();
        reference.setCustomEntity(this.entity);
        return reference;
    }
}
