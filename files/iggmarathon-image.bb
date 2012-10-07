inherit image chumby-info

IMAGE_PREPROCESS_COMMAND = "create_etc_timestamp"

XSERVER ?= "xserver-xorg \
           xf86-input-evdev \
           xf86-input-mouse \
           xf86-video-fbdev \
           xf86-input-keyboard \
"

DEPENDS = "task-base"


IMAGE_INSTALL += "task-boot \
# stuff below needs a proper task
            initscripts update-rc.d \
            openssh \
            networkmanager cnetworkmanager \
            xinetd \
            util-linux-ng-mount util-linux-ng-umount \
            udev bash bash-sh wpa-supplicant wireless-tools \
            rt73-firmware \
            vim \
            task-base kernel-modules \
            task-x11 \
            ${XSERVER} \
            strace \
            screen \
            midori \
            freerdp \
            lsof file iotop sysstat chumby-otg-debug\
           "

export IMAGE_BASENAME = "iggmarathon-image"
IMAGE_LINGUAS = ""
