package me.xginko.villageroptimizer.logging;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.Translator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public enum TranslatableMapper implements BiConsumer<TranslatableComponent, Consumer<Component>> {
    INSTANCE;

    public static final @NotNull ComponentFlattener FLATTENER = ComponentFlattener.basic().toBuilder()
            .complexMapper(TranslatableComponent.class, TranslatableMapper.INSTANCE)
            .build();

    @Override
    public void accept(
            final @NotNull TranslatableComponent translatableComponent,
            final @NotNull Consumer<Component> componentConsumer
    ) {
        final Locale locale = Locale.getDefault();
        for (final Translator source : GlobalTranslator.translator().sources()) {
            if (source.translate(translatableComponent.key(), locale) != null) {
                componentConsumer.accept(GlobalTranslator.render(translatableComponent, locale));
                return;
            }
        }
        final @Nullable String fallback = translatableComponent.fallback();
        if (fallback == null) {
            return;
        }
        for (final Translator source : GlobalTranslator.translator().sources()) {
            if (source.translate(fallback, locale) != null) {
                componentConsumer.accept(GlobalTranslator.render(Component.translatable(fallback), locale));
                return;
            }
        }
    }
}