---
layout: post
category : sdcard
tags : [falconwing, userland, angstrom]
---
Once we have the kernel running, we need to get an environment to boot
into.  Up until now, we've been using the stock chumby environment, but in
the interest of flexibility, let's see if we can't get a more standard
environment going.  I'm going to be honest here: I'm going to massively
cheat.  But it'll at least get us to the point where we can work on the
meat of the SD project, rather than fiddling around with generating a root
filesystem from scratch.

Not Playing Fair
----------------
Here's the cheat: I already have a root filesystem from another project
that's perfectly serviceable.   Another project I'm working on uses an
Angstrom root filesystem, and has an ARMV5te processor.  Even better, the
root filesystem is in a tarball, so all I have to do is extract it onto the
SD card and I'm good to go.

I want to preserve the stock root filesystems on the SD card, in case I
want to go back.  Fortunately the card has a "storage" partition stuck on
the end, which is ripe for overwriting.  So our root partition will end up
being /dev/mmcblk0p6.

Because I do development on a remote system, and I don't have any way of
writing to an ext4 filesystem on my Macbook aside from virtual machines,
I'm going to take advantage of SSH tunnels.  On the board, ensure that
partition 6 is unmounted, formatted, and remounted at a known location:
    umount /dev/mmcblk0p6
    mkfs.ext3 /dev/mmcblk0p6
    mount /dev/mmcblk0p6 /mnt/storage

Now, ensure that the board is on the network.  How you do this depends on
your network, but for me I plugged in an rt73 and ran wpa_supplicant
manually.  E.g.:
    wpa_passphrase net_name passphrase > /tmp/wpa.conf
    wpa_supplicant -c /tmp/wpa.conf -i wlan0 -B
    sleep 5
    udhcpc -i wlan0

Then, on the development machine, pipe the root filesystem over SSH.  Now,
I can't remember the exact command, but it was something along the lines
of:
    ssh root@192.168.1.5 "tar xvz -C /mnt/storage/" < rootfs.tar.gz

After around ten minutes, /mnt/storage/ should have a complete root
filesystem.  Modify the kernel to connect using "root=/dev/mmcblk0p6", and
you should have yourself an Angstrom root filesystem


Migrating Cards
---------------
The SD card I've been using is 1GB, which will be a bit tight for a
development system.  Compound that with the fact that half of the
filesystem is taken up by the two redundant root filesystems from the
chumby OS, plus a persistent storage partition and the boot partition, and
we're left with only around 300 MB.

Fortunately, moving to a larger card isn't difficult.  For this little
exercise I'm going to use a 2GB microSD card inserted into a USB reader.

Plug the card in and make sure no partitions are mounted.  Sometimes the
automounter will decide to kick in.  If so, make sure to unmount the
partitions before continuing.

To start off with, copy the partition table and boot partition over.  Let's
assume it's about 24 megabytes:
    dd if=/dev/mmcblk0 of=/dev/sda bs=1M count=24

Next, delete all the partitions from #2 onwards, and create a new #2
partition that fills the whole disk:
    root@chb:~# fdisk -u /dev/sda

    The number of cylinders for this disk is set to 198246.
    There is nothing wrong with that, but this is larger than 1024,
    and could in certain setups cause problems with:
    1) software that runs at boot time (e.g., old versions of LILO)
    2) booting and partitioning software from other OSs
    (e.g., DOS FDISK, OS/2 FDISK)
    Warning: ignoring extra data in partition table 5
    Warning: ignoring extra data in partition table 5
    Warning: ignoring extra data in partition table 5
    Warning: invalid flag 0x4a,0x9b of partition table 5 will be corrected by w(rite)

    Command (m for help): d
    Partition number (1-5): 5

    Command (m for help): d
    Partition number (1-5): 4

    Command (m for help): d
    Partition number (1-4): 3

    Command (m for help): d
    Partition number (1-4): 2

    Command (m for help): n
    Command action
    e   extended
    p   primary partition (1-4)
    p
    Partition number (1-4): 2
    First sector (31255-3964927, default 31255): Using default value 31255
    Last sector or +size or +sizeM or +sizeK (31255-3964927, default 3964927): Using default value 3964927

    Command (m for help): p

    Disk /dev/sda: 2030 MB, 2030043136 bytes
    5 heads, 4 sectors/track, 198246 cylinders, total 3964928 sectors
    Units = sectors of 1 * 512 = 512 bytes

    Device Boot      Start         End      Blocks  Id System
    /dev/sda1               4       31254       15625+ 53 Unknown
    /dev/sda2           31255     3964927     1966836+ 83 Linux

    Command (m for help): w
    The partition table has been altered.

Ensure you have mkfs.ext4 and rsync installed:
    opkg install e2fsprogs-mke2fs rsync

Then, format your new partition and copy data over.  Ensure you preserve
hardlinks and stick to just the / filesystem:
    root@chb:~# mkfs.ext4 /dev/sda2
    mke2fs 1.42.1 (17-Feb-2012)
    warning: 189 blocks unused.

    Filesystem label=
    OS type: Linux
    Block size=4096 (log=2)
    Fragment size=4096 (log=2)
    Stride=0 blocks, Stripe width=0 blocks
    123120 inodes, 491520 blocks
    24585 blocks (5.00%) reserved for the super user
    First data block=0
    Maximum filesystem blocks=503316480
    15 block groups
    32768 blocks per group, 32768 fragments per group
    8208 inodes per group
    Superblock backups stored on blocks: 
            32768, 98304, 163840, 229376, 294912

    Allocating group tables: done                            
    Writing inode tables: done                            
    Creating journal (8192 blocks): done
    Writing superblocks and filesystem accounting information: done 

    root@chb:~# mount /dev/sda2 /media
    root@chb:~# rsync -x -a -H -v / /media/
    sending incremental file list
    ./
    boot.bin
    zImage
    bin/
    ...

Modify your kernel to boot off /dev/mmcblk0p2, load it onto the SD card,
and reboot.  Congratulations, you've got yourself a new root filesystem,
made almost from scratch.

Alternative Options
-------------------
If you don't happen to have a spare root filesystem lying around, then you
can always install Debian.  You want to grab the "debootstrap" package, and
extract it somewhere.  It uses very basic tools such as "ar" and "gzip" to
install Debian from an existing Linux install.  It should be enough to
format your new partition, mount it, then use debootstrap to install an
"armel" build onto the new partition.

For example, you can probably run the following and get an installed
system, assuming your target partition is mounted on /media/:

    ./debootstrap --arch armel sid http://ftp.us.debian.org/debian/
    mount -obind /sys /media/sys
    mount -obind /dev /media/dev
    mount -obind /proc /media/proc
    chroot /media

Then use apt-get to install any packages you think you'll need, such as
wpa-supplicant or build-essential.

Installing Essentials
---------------------
Now that we have a usable root filesystem, we should get essentials for
doing dev work.  Fortunately we're using Angstrom, so it should be a simple
matter of "opkg install".  Update the list of packages:
    opkg update

Then install the basic compiler toolchain:
    opkg install libc6-dev openssl-dev libcurl-dev curl curl-dev perl-dev \
                 perl-dev perl-modules python-modules \
                 util-linux gcc-symlinks binutils gcc g++ make 

Fix symlinks that don't really work.  For example, "ar" is linked to the
limited busybox version rather than the full GCC one.  Correct that:
    rm /usr/bin/ar
    ln -s arm-angstrom-linux-gnueabi-ar /usr/bin/ar

To test that everything works, compile git from scratch.  This is also
useful, as there doesn't appear to be a "git" package in the default
Angstrom distribution:
    mkdir src
    cd src
    curl --insecure -O https://nodeload.github.com/git/git/zipball
    unzip zipball
    cd git-git-*
    make install

This will take a very long time.  Probably around an hour, maybe two.  But
at the end of it you'll have proven that the compiler works, that the board
is stable, and as a bonus you'll have a working copy of git.

Next up: Developing an SD protocol simulator.
