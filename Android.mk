LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_STATIC_JAVA_LIBRARIES = android-support-v4
LOCAL_STATIC_JAVA_LIBRARIES += android-support-v7-recyclerview
LOCAL_STATIC_JAVA_LIBRARIES += android-support-v7-appcompat
LOCAL_STATIC_JAVA_LIBRARIES += android-support-design

LOCAL_RESOURCE_DIR = \
        $(LOCAL_PATH)/res \
        frameworks/support/v7/recyclerview/res \
        frameworks/support/v7/appcompat/res \
        frameworks/support/design/res

LOCAL_AAPT_FLAGS := \
        --auto-add-overlay \
        --extra-packages android.support.v7.recyclerview \
        --extra-packages android.support.v7.appcompat \
        --extra-packages android.support.design

LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := $(call all-java-files-under, src)
LOCAL_MIN_SDK_VERSION := 24
LOCAL_PACKAGE_NAME := MusicFX
LOCAL_CERTIFICATE := platform

include $(BUILD_PACKAGE)
