package com.htv.starter.grpc;

import com.htv.starter.core.ContextValues;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.auth.x500.X500Principal;

/**
 * Đọc CN của chứng chỉ client (đã được TLS xác thực với CA tin cậy) và đặt vào {@link GrpcPeers}.
 * Giá trị không an toàn (ký tự lạ, quá dài) bị bỏ, coi như không có danh tính.
 */
public class PeerIdentityInterceptor implements ServerInterceptor {

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers,
                                                                 ServerCallHandler<ReqT, RespT> next) {
        String cn = commonName(call.getAttributes().get(Grpc.TRANSPORT_ATTR_SSL_SESSION));
        Context context = Context.current().withValue(GrpcPeers.SERVICE_NAME, cn);
        return Contexts.interceptCall(context, call, headers, next);
    }

    static String commonName(SSLSession session) {
        if (session == null) {
            return null;
        }
        try {
            Certificate[] chain = session.getPeerCertificates();
            if (chain != null && chain.length > 0 && chain[0] instanceof X509Certificate cert) {
                return ContextValues.safe(commonName(cert.getSubjectX500Principal()));
            }
        } catch (SSLPeerUnverifiedException e) {
            // client không trình chứng chỉ: không có danh tính
        }
        return null;
    }

    static String commonName(X500Principal principal) {
        try {
            for (Rdn rdn : new LdapName(principal.getName(X500Principal.RFC2253)).getRdns()) {
                if ("CN".equalsIgnoreCase(rdn.getType())) {
                    return String.valueOf(rdn.getValue());
                }
            }
        } catch (InvalidNameException e) {
            // chứng chỉ lạ: không có danh tính
        }
        return null;
    }
}
