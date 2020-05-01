package com.github.hslls.plugin;

import com.android.build.api.transform.QualifiedContent;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.github.hlls.transform.EasyTransform;

import org.gradle.api.Project;

import java.io.File;
import java.util.Set;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.Modifier;
import javassist.NotFoundException;

public class GrayTransform extends EasyTransform {

    public GrayTransform(Project project) {
        super(project);
    }

    @Override
    public String getName() {
        return "GrayTransform";
    }

    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @Override
    public Set<? super QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT;
    }

    @Override
    protected boolean isJarFileNeedModify(File jarFile) {
        return true;
    }

    @Override
    protected boolean justModifyNotWriteBack(CtClass ctClass) {
        if (ctClass.isInterface() ||
                ((ctClass.getModifiers() & Modifier.ABSTRACT) == Modifier.ABSTRACT)) {
            return false;
        }

        boolean hasModified = false;
        CtMethod[] methods = ctClass.getMethods();
        for (CtMethod m : methods) {
            String methodName = m.getName();
            if (methodName.equals("onCreate")) {
                CtClass[] types;
                try {
                    types = m.getParameterTypes();
                } catch (NotFoundException e) {
                    break;
                }
                boolean insert = ((types.length == 1) && types[0].getName().equals("android.os.Bundle"));
                // ((types.length == 2) && types[0].getName().equals("android.os.Bundle") && types[1].getName().equals("android.os.PersistableBundle"));
                if (!insert) {
                    break;
                }

                if (!ctClass.getName().equals(m.getDeclaringClass().getName())) {
                    try {
                        m = CtNewMethod.delegator(m, ctClass);
                        ctClass.addMethod(m);
                    } catch (CannotCompileException e) {
                        break;
                    }
                }

                try {
                    m.insertAfter(
                            "android.graphics.Paint paint = new android.graphics.Paint();" +
                                    "android.graphics.ColorMatrix cm = new android.graphics.ColorMatrix();" +
                                    "cm.setSaturation(0f);" +
                                    "paint.setColorFilter(new android.graphics.ColorMatrixColorFilter(cm));" +
                                    "getWindow().getDecorView().setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, paint);");
                } catch (CannotCompileException e) {
                    break;
                }
                hasModified = true;
            }
        }

        return hasModified;
    }
}