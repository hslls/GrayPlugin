package com.github.hslls.plugin;

import com.android.build.gradle.AppExtension;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.ExtensionContainer;

public class GrayPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        ExtensionContainer ec = project.getExtensions();
        ec.create("grayconfig", GrayConfig.class);
        AppExtension app = ec.getByType(AppExtension.class);
        app.registerTransform(new GrayTransform(project));
    }

}