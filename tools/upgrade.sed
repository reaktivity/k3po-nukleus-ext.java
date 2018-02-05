#
# HOWTO Upgrade
#
# find . -name "*.rpt" | xargs -L 1 sed -i "~" -f tools/upgrade.sed
#

#
# abort and aborted
#
s/^abort$/read abort/
s/^aborted$/write aborted/

#
# nukleus:window
# nukleus:throttle
# nukleus:padding
#
/nukleus\:window/d
/nukleus\:throttle/d
/nukleus\:padding/d

