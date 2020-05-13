package com.github.hslls.plugin;

import com.android.build.gradle.BaseExtension;
import com.github.hlls.transform.EasyTransform;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.ExtensionContainer;

public class GrayPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        ExtensionContainer ec = project.getExtensions();
        ec.create("grayConfigs", GrayConfigs.class);

        BaseExtension extension = EasyTransform.getExtension(project);
        if (extension != null) {
            extension.registerTransform(new GrayTransform(project));
        }
    }

}