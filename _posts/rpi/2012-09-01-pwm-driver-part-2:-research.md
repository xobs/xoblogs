---
layout: post
category : rpi
tags : [hardware]
---

Now that we have the basis for working in the kernel, we need to figure
out what it is we want to do exactly.  Fortunately it's possible to
prototype this code in userspace and really get a feel for how the PWM
block behaves before moving it into the kernel.  In order to do this, we need
to know about memory-mapped registers, the PWM module on the Raspberry Pi's
CPU, and how to map physical memory into virtual memory.  Oh, and it helps
to have an oscilloscope handy, too.

Getting a Compiler
------------------
We're going to be writing little helper programs, so it can be handy to
have a compiler on your Raspberry Pi.  With Debian, installing one is a
simple process.  There are several toy programs discussed here.  For ease
of development, it is recommended you compile them on the device.

To install a compiler, run:
    apt-get install build-essential
To compile and run program, run
    gcc file.c -o file
    ./file

A Bit About Registers
---------------------
Hardware configuration registers are special areas of memory that allow for
control over various peripherals.  Generally a hardware block is defined as
occupying a fixed area of memory of around four kilobytes, and has multiple
configuration registers available within that area.  This is where the
reference manual comes in very handy, as it will tell you both what a
peripheral's offset is, and what each register does.  Frequently, each
register is 32-bits, and performs multiple functions.

Unfortunately, while the limited BCM2835 reference manual tells us the PWM
register offsets from the start of the PWM block, it doesn't tell us where
the PWM block is located!  Internet searches reveal that the PWM block is
located at **BCM2708_PERI_BASE+0x20C000**, or at offset **0x2020c000**.
That means that the "Address Offset" value mentioned in the reference
manual must be added to **0x2020c000** in order to find the real address.
Hence, the PWM Status register (0x4) may be found at address **0x2020c004**.

A Bit About Memory
------------------
Modern operating systems such as Linux support memory protection and memory
remapping.  This allows for increased security, and prevents memory
fragmentation.  In the Bad Old Days of DOS and MacOS 9, it was possible for
any program to scribble anywhere in memory.  This was the source of many
crashes, and since the move towards protected memory, the world has become
a more reliable place.

Not even root under Linux has the ability to randomly write to various
memory addresses.  If it attempts to write to memory segment that it
doesn't have permission to, it will cause the kernel to generate a
segmentation fault, and the program will likely crash.

It's possible to gain direct access to specific memory addresses, provided
there aren't any special hardware restrictions in place.  To do this, open
the special file /dev/mem and run mmap() on it with a specific address.  To
map the entire PWM block mentioned above and store it in a pointer called
**long \*pwm_block**, use the following code:
    int fd = open("/dev/mem", O_RDWR|O_SYNC);
    pwm_block = mmap(NULL, 4096, PROT_READ|PROT_WRITE, MAP_SHARED, fd, (char *)0x2020c000);
You should now be able to read pwm_block[0] and obtain the PWM Control
register status.

**Warning:** Because pwm_block is defined as a long pointer, its array
index will scale.  Thus, to get PWM Status, you'd read pwm_block[1], and
not pwm_block[4].  If this is confusing, you can define a macro that undoes
this scaling:
    #define PWM_REG(x) (pwm_block[x/sizeof(*pwm_block)])

Good Artists Copy
-----------------
If someone has already figured out how the PWM works, it's a great place to
start our investigation.  Frank Buss [has posted
code](http://www.frank-buss.de/raspberrypi/pwm.c) that drives a servo motor
using the PWM pin.  I'm going to refer to this file as "servo.c", because
that's what I renamed it to, and because it deals exclusively with servo
mode.  Compile and run the code, then hook up a scope just to
verify things are working.  If you don't get a signal with known-working
code, there's no point in trying to write your own code that does the same
thing because you won't be able to tell if it works.

The servo.c code is very interesting for a number of reasons:

1. It sets up GPIO alternate functions
2. It sets up PWMCLK registers.  These aren't documented in the reference manual.
3. It uses FIFO mode, rather than the usual counter method employed by most
PWMs.

A Note on Alternate Functions
-----------------------------
System-on-Chip CPUs such as the one used in the Raspberry Pi have multiple
features multiplexed on a single pin.  Thus, while the PWM block can drive
two PWMs (PWM0 and PWM1), there are several pins on the chip where the
signal can actually appear.  This gives system designers more flexibility
when it comes to design, because it may be easier to route a wire from one
pin versus another.

The Raspberry Pi actually exposes PWM0 in two places.  GPIO40 is routed to
the right headphone channel, and GPIO18 is routed to the breakout header.
Both pins may be configured to emit the signal PWM0 at the same time by
setting their Alternate Function settings appropriately.

There is a table in section 6.2 (page 102) of the reference manual that
lists each GPIO and what functions it can be configured to perform.  Notice
that GPIO18, the pin present on the expansion header, lists PWM0 as AF5.
Therefore, in order to use PWM0, we must set GPIO18 to AF5.

Frank's code does a reasonable job dealing with the supreme weirdness that
is the BCM AF selector.  Ten different GPIO AF selects are packed into a
single 32-bit register, with each GPIO getting three bits.  Counting from 0
to 7, the value in a particular AF offset can be input, output, AF5, AF4,
AF0, AF1, AF2, or AF3.  Notice that weird discontinuity.  That's why
Frank's SET_GPIO_ALT macro is so long.  Unfortunately it contains a subtle
bug in that it doesn't clear the value of a field before writing it, so for
example you can go from Input mode to AF5, but you can't go from AF5 back
down to Input mode, because that would require clearing bits.  The
following macros will let you switch between any AF values indefinitely:

    #define GPIO_BANK(g) (*(gpio+(((g)/10))))
    #define GPIO_FSET(g,a) (GPIO_BANK(g) =  \
              (GPIO_BANK(g) & (~(7<<(((g)%10)*3)))) | ((a)<<(((g)%10)*3)))
    #define GPIO_FGET(g) (GPIO_BANK(g) >> ((((g)%10)*3)) & 7)

It can be useful to pull AF-setting code out into a separate program to
ensure it's working alright.  I ended up doing this in order to chase down
the SET_GPIO_ALT macro bug, and the resulting program is very handy:
[af.c](https://github.com/xobs/rpi-tools/blob/master/af.c).

Those Clock Registers
---------------------
The clock system is wholly undocumented in publicly-released manuals,
so we'll have to rely on Frank's comments here.  It appears as though
there's a 19.2 MHz clock that gets divided down, and it looks like
it's a twelve-bit divider.  Additionally, the magical value 0x5A000000 must
be OR-ed with any clock change.  There is a nice set of comments that
describes how the desired master clock frequency is derived.

The clock-setting code is also a very good candidate for pulling into its
own program in order to better understand it.  I did that, and the
resulting program is called
[pwm-clk.c](https://github.com/xobs/rpi-tools/blob/master/pwm-clk.c).

If we run Frank's original pwm.c code and then kill it with Control-C, we
notice on the oscilloscope that the PWM continues to run.  We can then
begin to play with the clock settings using our new pwm-clk program.
Notice how if the master clock is set to 8000, the period doubles.  If it's
set to 32000, the period halves.  Also, the overall period is always equal
to the frequency we set, as long as it's within range.  Very useful indeed.


The PWM Registers
-----------------
Finally we get to the meat of the problem: How do we set the PWM to an
arbitrary frequency and an arbitrary duty cycle.  A careful
reading of Frank's code reveals that he's cleverly using a unique feature
of the BCM chip:  Rather than outputting a ratio of high/low, he outputs a
bit pattern where the lower *n* bits are set, allowing him to get a
reasonably accurate and stable servo control loop.

We're interested in obtaining variable frequencies with variable duty
cycles over a wide range, so we'd like to use what the manual refers to as
"M/S Mode".  It notes that this mode "may be preferred if high frequency
modulation is not required or has negative effects."  Sounds like what we
need.

To fiddle with PWM registers, it can be handy to create *another* toy
program.  I made this one fancier, and made each register and each field
individually addressable.  It's called
[pwm.c](https://github.com/xobs/rpi-tools/blob/master/pwm.c).

Run Frank's program to set up PWM mode, then use the PWM register program
to dump the current register state:
    ./pwm -d
Reading the manual, it looks like we want to start by to enabling MSIE
mode, and taking it out of serializer mode:
    ./pwm -w PWM.CTL.MSEN1=1 -w PWM.CTL.MODE1=0

The waveform stops completely and is silenced.  Curious.  But wait, it
looks like the "Channel 1 Data" field is invalid.  Let's try setting it to
something.  Anything.  How about 1:
    ./pwm -w PWM.DAT1.DAT=1   

If we do this, we see a waveform appear on the scope.  Hooray.  What if we
put in different values?  Try changing the value of DAT around to get a
feel for what it does.  Every doubling of DAT doubles the amount of time
the waveform spends high.  The frequency remains the same.

Now take a look at the RNG1 register.  Set a few different values, such as
ratios of the default value of 320:
    ./pwm -w PWM.RNG1.RNG=640
    ./pwm -w PWM.RNG1.RNG=160
Notice how the length of the pulse changes.  Double RNG1 and you halve the
frequency.  Cut RNG1 in half and double the frequency.  It looks like the
ratio of RNG1 to DAT1 defines the duty cycle, which is exactly what we
need.

Putting It Together
-------------------
Now that we have all of the necessary pieces, we can put them together to
come up with a way to set a specific frequency with a specific duty cycle.

We know what our master frequency will run at, because we set it in the
PWM_CLK register.  We need to pick a master frequency that can be divided
down into both the RNG1 value (in order to have an accurate frequency), and
also into the DAT1 value (in order to have an accurate duty cycle).

I don't know of a good way to find this value, so I'm just going to punt
and make the value user-adjustable.

Once we find the master frequency, we can divide it down using the RNG
register to get our desired output frequency.  Then we can take a fraction
of RNG and store it in DAT in order to get our duty cycle.

If you like, you can create a program that combines all of the code
presented here in order to verify you actually understand how the PWM
functions.  But I'm going to go straight into kernel development, and begin
work on the actual driver.

Next time: Implementing the kernel driver.
