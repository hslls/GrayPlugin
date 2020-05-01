package com.github.hslls.plugin;

import com.android.build.gradle.AppExtension;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class GrayPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        AppExtension app = project.getExtensions().getByType(AppExtension.class);
        app.registerTransform(new GrayTransform(project));
    }

}