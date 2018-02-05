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

#
# nukleus:data.ext
# nukleus:data.empty
# nukleus:data.null
# nukleus:end.ext
#
s/nukleus\:data.ext/nukleus\:transfer.ext/
s/nukleus\:data.empty/nukleus\:transfer.empty/
s/nukleus\:data.null/nukleus\:transfer.null/
s/nukleus\:end.ext/nukleus\:transfer.ext/

