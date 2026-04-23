package org.wodrol.brakoffpc.web;

import org.springframework.stereotype.Service;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class NetworkInfoService {

    public List<String> detectLocalIpv4Addresses() {
        Set<String> addresses = new LinkedHashSet<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual()) {
                    continue;
                }

                Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress inetAddress = inetAddresses.nextElement();
                    if (inetAddress instanceof Inet4Address inet4Address
                            && !inet4Address.isLoopbackAddress()
                            && !inet4Address.isLinkLocalAddress()) {
                        addresses.add(inet4Address.getHostAddress());
                    }
                }
            }
        } catch (SocketException ignored) {
            return List.of();
        }

        List<String> sorted = new ArrayList<>(addresses);
        sorted.sort(Comparator.naturalOrder());
        return sorted;
    }
}
