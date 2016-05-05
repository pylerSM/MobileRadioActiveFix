package com.pyler.mobileradioactivefix;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

public class MobileRadioActiveFix implements IXposedHookLoadPackage {

    private static final String PACKAGE_NAME = "android";
    private static final String CLASS_NAME = "com.android.server.NetworkManagementService";
    private static final String METHOD_NAME = "notifyInterfaceClassActivity";

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {

        if (!lpparam.packageName.equals(PACKAGE_NAME))
            return;

        findAndHookMethod(
                CLASS_NAME,
                lpparam.classLoader,
                METHOD_NAME,
                int.class, int.class, long.class, boolean.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        param.args[3] = true;
                    }
                }
        );

    }
}