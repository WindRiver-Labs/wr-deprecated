SECTION = "console/network"
SUMMARY = "Internet Software Consortium DHCP package"
DESCRIPTION = "DHCP (Dynamic Host Configuration Protocol) is a protocol \
which allows individual devices on an IP network to get their own \
network configuration information from a server.  DHCP helps make it \
easier to administer devices."

HOMEPAGE = "http://www.isc.org/"

LICENSE = "ISC"
LIC_FILES_CHKSUM = "file://LICENSE;beginline=4;md5=004a4db50a1e20972e924a8618747c01"

DEPENDS = "openssl libcap zlib"

SRC_URI = "https://ftp.isc.org/isc/dhcp/4.4.2-P1/dhcp-4.4.2-P1.tar.gz \
           https://ftp.isc.org/isc/bind9/9.11.32/bind-9.11.32.tar.gz;name=bind;downloadfilename=bind.tar.gz;unpack=0 \
           file://0001-define-macro-_PATH_DHCPD_CONF-and-_PATH_DHCLIENT_CON.patch \
           file://0002-dhclient-dbus.patch \
           file://0003-link-with-lcrypto.patch \
           file://0004-Fix-out-of-tree-builds.patch \
           file://0005-dhcp-client-fix-invoke-dhclient-script-failed-on-Rea.patch \
           file://0006-Add-configure-argument-to-make-the-libxml2-dependenc.patch \
           file://0007-remove-dhclient-script-bash-dependency.patch \
           file://0008-dhcp-correct-the-intention-for-xml2-lib-search.patch \
           file://0009-Workaround-busybox-limitation-in-Linux-dhclient-scri.patch \
           file://0010-bind-version-update-to-latest-version.patch \
           file://0011-bind-Makefile.in-disable-backtrace.patch \
           file://0012-Makefile.am-disable-dhcp-relay-build.patch \
           file://init-server file://default-server \
           file://dhclient.conf file://dhcpd.conf \
           file://dhcpd.service file://dhcpd6.service \
           file://dhclient-systemd-wrapper \
           file://dhclient.service \
           "

SRC_URI[md5sum] = "3089a1ebd20a802ec0870ae337d43907"
SRC_URI[sha256sum] = "b05e04337539545a8faa0d6ac518defc61a07e5aec66a857f455e7f218c85a1a"
SRC_URI[bind.md5sum] = "0d029dd06ca60c6739c3189c999ef757"
SRC_URI[bind.sha256sum] = "cbf8cb4b74dd1452d97c3a2a8c625ea346df8516b4b3508ef07443121a591342"

UPSTREAM_CHECK_URI = "http://ftp.isc.org/isc/dhcp/"
UPSTREAM_CHECK_REGEX = "(?P<pver>\d+\.\d+\.(\d+?))/"

S = "${WORKDIR}/dhcp-4.4.2-P1"

inherit autotools-brokensep systemd useradd update-rc.d

USERADD_PACKAGES = "${PN}-server"
USERADD_PARAM_${PN}-server = "--system --no-create-home --home-dir /var/run/${BPN} --shell /bin/false --user-group ${BPN}"

SYSTEMD_PACKAGES = "${PN}-server ${PN}-client"
SYSTEMD_SERVICE_${PN}-server = "dhcpd.service dhcpd6.service"
SYSTEMD_AUTO_ENABLE_${PN}-server = "disable"

SYSTEMD_SERVICE_${PN}-client = "dhclient.service"
SYSTEMD_AUTO_ENABLE_${PN}-client = "disable"

INITSCRIPT_PACKAGES = "dhcp-server"
INITSCRIPT_NAME_dhcp-server = "dhcp-server"
INITSCRIPT_PARAMS_dhcp-server = "defaults"

CFLAGS += "-D_GNU_SOURCE -fcommon"
LDFLAGS_append = " -pthread"

EXTRA_OECONF = "--with-srv-lease-file=${localstatedir}/lib/dhcp/dhcpd.leases \
                --with-srv6-lease-file=${localstatedir}/lib/dhcp/dhcpd6.leases \
                --with-cli-lease-file=${localstatedir}/lib/dhcp/dhclient.leases \
                --with-cli6-lease-file=${localstatedir}/lib/dhcp/dhclient6.leases \
                --enable-paranoia --disable-static \
                --with-randomdev=/dev/random \
                --enable-libtool \
               "

PACKAGECONFIG ?= ""
PACKAGECONFIG[bind-httpstats] = "--with-libxml2,--without-libxml2,libxml2"

EXTRA_OEMAKE += "LIBTOOL='${S}/${HOST_SYS}-libtool'"

# Enable shared libs per dhcp README
do_configure_prepend () {
    cp configure.ac+lt configure.ac
    rm ${S}/bind/bind.tar.gz
    mv ${WORKDIR}/bind.tar.gz ${S}/bind/
}

do_compile_prepend() {
    rm -rf ${S}/bind/bind-9.11.32/
    tar xf ${S}/bind/bind.tar.gz -C ${S}/bind
    install -m 0755 ${STAGING_DATADIR_NATIVE}/gnu-config/config.guess ${S}/bind/bind-9.11.32/
    install -m 0755 ${STAGING_DATADIR_NATIVE}/gnu-config/config.sub ${S}/bind/bind-9.11.32/
    cp -fpR ${S}/m4/*.m4 ${S}/bind/bind-9.11.32/libtool.m4/
    rm -rf ${S}/bind/bind-9.11.32/libtool
    install -m 0755 ${S}/${HOST_SYS}-libtool ${S}/bind/bind-9.11.32/
}

do_install_append () {
    install -d ${D}${sysconfdir}/default
    install -d ${D}${sysconfdir}/dhcp
    install -m 0644 ${WORKDIR}/default-server ${D}${sysconfdir}/default/dhcp-server
    echo 'INTERFACES="eth0"' > ${D}${sysconfdir}/default/dhcp-client

    rm -f ${D}${sysconfdir}/dhclient.conf*
    rm -f ${D}${sysconfdir}/dhcpd.conf*
    install -m 0644 ${WORKDIR}/dhclient.conf ${D}${sysconfdir}/dhcp/dhclient.conf
    install -m 0644 ${WORKDIR}/dhcpd.conf ${D}${sysconfdir}/dhcp/dhcpd.conf

    install -d ${D}${base_sbindir}
    if [ "${sbindir}" != "${base_sbindir}" ]; then
        mv ${D}${sbindir}/dhclient ${D}${base_sbindir}/
    fi
    install -m 0755 ${S}/client/scripts/linux ${D}${base_sbindir}/dhclient-script

    if ${@bb.utils.contains('DISTRO_FEATURES','systemd','true','false',d)}; then
        install -d ${D}${systemd_unitdir}/system
        install -m 0644 ${WORKDIR}/dhcpd.service ${D}${systemd_unitdir}/system
        install -m 0644 ${WORKDIR}/dhcpd6.service ${D}${systemd_unitdir}/system
        sed -i -e 's,@SBINDIR@,${sbindir},g' ${D}${systemd_unitdir}/system/dhcpd*.service
        sed -i -e 's,@SYSCONFDIR@,${sysconfdir},g' ${D}${systemd_unitdir}/system/dhcpd*.service
        sed -i -e 's,@base_bindir@,${base_bindir},g' ${D}${systemd_unitdir}/system/dhcpd*.service
        sed -i -e 's,@localstatedir@,${localstatedir},g' ${D}${systemd_unitdir}/system/dhcpd*.service

        install -m 0755 ${WORKDIR}/dhclient-systemd-wrapper ${D}${base_sbindir}/dhclient-systemd-wrapper
        install -m 0644 ${WORKDIR}/dhclient.service ${D}${systemd_unitdir}/system
        sed -i -e 's,@SYSCONFDIR@,${sysconfdir},g' ${D}${systemd_unitdir}/system/dhclient.service
        sed -i -e 's,@BASE_SBINDIR@,${base_sbindir},g' ${D}${systemd_unitdir}/system/dhclient.service
    else
        install -d ${D}${sysconfdir}/init.d
        install -m 0755 ${WORKDIR}/init-server ${D}${sysconfdir}/init.d/dhcp-server
    fi

    # Use the libraries from dhcp-relay to avoid conflicts
    rm -f ${D}${libdir}/libdhcp.* ${D}${libdir}/libdns.* \
        ${D}${libdir}/libirs.* ${D}${libdir}/libisccfg.* \
        ${D}${libdir}/libisc.* ${D}${libdir}/libomapi.*
}

PACKAGES += "dhcp-libs dhcp-server dhcp-server-config dhcp-client dhcp-omshell"

PACKAGES_remove = "${PN}"
RDEPENDS_${PN}-server += "dhcp-relay"
RDEPENDS_${PN}-client += "dhcp-relay ${@bb.utils.contains('DISTRO_FEATURES', 'systemd', 'iproute2', '', d)}"
RDEPENDS_${PN}-omshell += "dhcp-relay"
RDEPENDS_${PN}-dev = ""
RDEPENDS_${PN}-staticdev = ""

FILES_${PN}-libs = "${libdir}/lib*.so.*"

FILES_${PN}-server = "${sbindir}/dhcpd ${sysconfdir}/init.d/dhcp-server"
RRECOMMENDS_${PN}-server = "dhcp-server-config"

FILES_${PN}-server-config = "${sysconfdir}/default/dhcp-server ${sysconfdir}/dhcp/dhcpd.conf"

FILES_${PN}-client = "${base_sbindir}/dhclient \
                      ${base_sbindir}/dhclient-script \
                      ${sysconfdir}/dhcp/dhclient.conf \
                      ${sysconfdir}/default/dhcp-client \
                      ${base_sbindir}/dhclient-systemd-wrapper \
                     "

FILES_${PN}-omshell = "${bindir}/omshell"

pkg_postinst_dhcp-server() {
    mkdir -p $D/${localstatedir}/lib/dhcp
    touch $D/${localstatedir}/lib/dhcp/dhcpd.leases
    touch $D/${localstatedir}/lib/dhcp/dhcpd6.leases
}

pkg_postinst_dhcp-client() {
    mkdir -p $D/${localstatedir}/lib/dhcp
}

pkg_postrm_dhcp-server() {
    rm -f $D/${localstatedir}/lib/dhcp/dhcpd.leases
    rm -f $D/${localstatedir}/lib/dhcp/dhcpd6.leases

    if ! rmdir $D/${localstatedir}/lib/dhcp 2>/dev/null; then
        echo "Not removing ${localstatedir}/lib/dhcp as it is non-empty."
    fi
}

pkg_postrm_dhcp-client() {
    rm -f $D/${localstatedir}/lib/dhcp/dhclient.leases
    rm -f $D/${localstatedir}/lib/dhcp/dhclient6.leases

    if ! rmdir $D/${localstatedir}/lib/dhcp 2>/dev/null; then
        echo "Not removing ${localstatedir}/lib/dhcp as it is non-empty."
    fi
}

PARALLEL_MAKE = ""
