package com.waterquality.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.protobuf.services.ProtoReflectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
public class GrpcServer {

    private static final Logger log = LoggerFactory.getLogger(GrpcServer.class);

    @Value("${grpc.server.port:9090}")
    private int grpcPort;

    @Value("${grpc.server.enabled:true}")
    private boolean enabled;

    private final WaterQualityGrpcService grpcService;
    private Server server;

    public GrpcServer(WaterQualityGrpcService grpcService) {
        this.grpcService = grpcService;
    }

    @PostConstruct
    public void start() throws IOException {
        if (!enabled) {
            log.info("gRPC server is disabled by configuration");
            return;
        }
        server = ServerBuilder
                .forPort(grpcPort)
                .addService(grpcService)
                .addService(ProtoReflectionService.newInstance())
                .maxInboundMessageSize(10 * 1024 * 1024) // 10 MB
                .build()
                .start();
        log.info("gRPC server started on port {}", grpcPort);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down gRPC server...");
            GrpcServer.this.stop();
        }));
    }

    @PreDestroy
    public void stop() {
        if (server != null && !server.isShutdown()) {
            try {
                server.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                server.shutdownNow();
            }
            log.info("gRPC server stopped");
        }
    }
}
