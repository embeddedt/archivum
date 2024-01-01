package com.example.examplemod;

import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reflections.Reflections;

// The value here should match an entry in the META-INF/mods.toml file
@Mod("examplemod")
public class ExampleMod
{
    // Directly reference a log4j logger.
    private static final Logger LOGGER = LogManager.getLogger();

    public ExampleMod() {
        Reflections reflections = new Reflections(Mod.class.getPackage().getName());
        for (Class<?> clz : reflections.getTypesAnnotatedWith(Mod.class)) {
            LOGGER.info("Found class annotated with Mod: {}", clz.getName());
        }
    }
}
