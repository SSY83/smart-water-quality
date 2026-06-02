package com.waterquality.proto;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.56.1)",
    comments = "Source: water_quality_message.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class WaterQualityEdgeServiceGrpc {

  private WaterQualityEdgeServiceGrpc() {}

  public static final String SERVICE_NAME = "com.waterquality.proto.WaterQualityEdgeService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.waterquality.proto.WaterQualityMessage,
      com.waterquality.proto.EdgeCommand> getStreamAnalysisResultsMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "StreamAnalysisResults",
      requestType = com.waterquality.proto.WaterQualityMessage.class,
      responseType = com.waterquality.proto.EdgeCommand.class,
      methodType = io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
  public static io.grpc.MethodDescriptor<com.waterquality.proto.WaterQualityMessage,
      com.waterquality.proto.EdgeCommand> getStreamAnalysisResultsMethod() {
    io.grpc.MethodDescriptor<com.waterquality.proto.WaterQualityMessage, com.waterquality.proto.EdgeCommand> getStreamAnalysisResultsMethod;
    if ((getStreamAnalysisResultsMethod = WaterQualityEdgeServiceGrpc.getStreamAnalysisResultsMethod) == null) {
      synchronized (WaterQualityEdgeServiceGrpc.class) {
        if ((getStreamAnalysisResultsMethod = WaterQualityEdgeServiceGrpc.getStreamAnalysisResultsMethod) == null) {
          WaterQualityEdgeServiceGrpc.getStreamAnalysisResultsMethod = getStreamAnalysisResultsMethod =
              io.grpc.MethodDescriptor.<com.waterquality.proto.WaterQualityMessage, com.waterquality.proto.EdgeCommand>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "StreamAnalysisResults"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.waterquality.proto.WaterQualityMessage.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.waterquality.proto.EdgeCommand.getDefaultInstance()))
              .setSchemaDescriptor(new WaterQualityEdgeServiceMethodDescriptorSupplier("StreamAnalysisResults"))
              .build();
        }
      }
    }
    return getStreamAnalysisResultsMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.waterquality.proto.AlertRequest,
      com.waterquality.proto.AlertResponse> getReportAlertMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ReportAlert",
      requestType = com.waterquality.proto.AlertRequest.class,
      responseType = com.waterquality.proto.AlertResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.waterquality.proto.AlertRequest,
      com.waterquality.proto.AlertResponse> getReportAlertMethod() {
    io.grpc.MethodDescriptor<com.waterquality.proto.AlertRequest, com.waterquality.proto.AlertResponse> getReportAlertMethod;
    if ((getReportAlertMethod = WaterQualityEdgeServiceGrpc.getReportAlertMethod) == null) {
      synchronized (WaterQualityEdgeServiceGrpc.class) {
        if ((getReportAlertMethod = WaterQualityEdgeServiceGrpc.getReportAlertMethod) == null) {
          WaterQualityEdgeServiceGrpc.getReportAlertMethod = getReportAlertMethod =
              io.grpc.MethodDescriptor.<com.waterquality.proto.AlertRequest, com.waterquality.proto.AlertResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ReportAlert"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.waterquality.proto.AlertRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.waterquality.proto.AlertResponse.getDefaultInstance()))
              .setSchemaDescriptor(new WaterQualityEdgeServiceMethodDescriptorSupplier("ReportAlert"))
              .build();
        }
      }
    }
    return getReportAlertMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.waterquality.proto.ModelVersionRequest,
      com.waterquality.proto.ModelVersionResponse> getGetModelVersionMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetModelVersion",
      requestType = com.waterquality.proto.ModelVersionRequest.class,
      responseType = com.waterquality.proto.ModelVersionResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.waterquality.proto.ModelVersionRequest,
      com.waterquality.proto.ModelVersionResponse> getGetModelVersionMethod() {
    io.grpc.MethodDescriptor<com.waterquality.proto.ModelVersionRequest, com.waterquality.proto.ModelVersionResponse> getGetModelVersionMethod;
    if ((getGetModelVersionMethod = WaterQualityEdgeServiceGrpc.getGetModelVersionMethod) == null) {
      synchronized (WaterQualityEdgeServiceGrpc.class) {
        if ((getGetModelVersionMethod = WaterQualityEdgeServiceGrpc.getGetModelVersionMethod) == null) {
          WaterQualityEdgeServiceGrpc.getGetModelVersionMethod = getGetModelVersionMethod =
              io.grpc.MethodDescriptor.<com.waterquality.proto.ModelVersionRequest, com.waterquality.proto.ModelVersionResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetModelVersion"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.waterquality.proto.ModelVersionRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.waterquality.proto.ModelVersionResponse.getDefaultInstance()))
              .setSchemaDescriptor(new WaterQualityEdgeServiceMethodDescriptorSupplier("GetModelVersion"))
              .build();
        }
      }
    }
    return getGetModelVersionMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.waterquality.proto.HealthCheckRequest,
      com.waterquality.proto.HealthCheckResponse> getHealthCheckMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "HealthCheck",
      requestType = com.waterquality.proto.HealthCheckRequest.class,
      responseType = com.waterquality.proto.HealthCheckResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.waterquality.proto.HealthCheckRequest,
      com.waterquality.proto.HealthCheckResponse> getHealthCheckMethod() {
    io.grpc.MethodDescriptor<com.waterquality.proto.HealthCheckRequest, com.waterquality.proto.HealthCheckResponse> getHealthCheckMethod;
    if ((getHealthCheckMethod = WaterQualityEdgeServiceGrpc.getHealthCheckMethod) == null) {
      synchronized (WaterQualityEdgeServiceGrpc.class) {
        if ((getHealthCheckMethod = WaterQualityEdgeServiceGrpc.getHealthCheckMethod) == null) {
          WaterQualityEdgeServiceGrpc.getHealthCheckMethod = getHealthCheckMethod =
              io.grpc.MethodDescriptor.<com.waterquality.proto.HealthCheckRequest, com.waterquality.proto.HealthCheckResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "HealthCheck"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.waterquality.proto.HealthCheckRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.waterquality.proto.HealthCheckResponse.getDefaultInstance()))
              .setSchemaDescriptor(new WaterQualityEdgeServiceMethodDescriptorSupplier("HealthCheck"))
              .build();
        }
      }
    }
    return getHealthCheckMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static WaterQualityEdgeServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<WaterQualityEdgeServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<WaterQualityEdgeServiceStub>() {
        @java.lang.Override
        public WaterQualityEdgeServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new WaterQualityEdgeServiceStub(channel, callOptions);
        }
      };
    return WaterQualityEdgeServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static WaterQualityEdgeServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<WaterQualityEdgeServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<WaterQualityEdgeServiceBlockingStub>() {
        @java.lang.Override
        public WaterQualityEdgeServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new WaterQualityEdgeServiceBlockingStub(channel, callOptions);
        }
      };
    return WaterQualityEdgeServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static WaterQualityEdgeServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<WaterQualityEdgeServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<WaterQualityEdgeServiceFutureStub>() {
        @java.lang.Override
        public WaterQualityEdgeServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new WaterQualityEdgeServiceFutureStub(channel, callOptions);
        }
      };
    return WaterQualityEdgeServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     * <pre>
     * 双向流: 边缘端持续上报分析结果，云端下发指令/ACK
     * </pre>
     */
    default io.grpc.stub.StreamObserver<com.waterquality.proto.WaterQualityMessage> streamAnalysisResults(
        io.grpc.stub.StreamObserver<com.waterquality.proto.EdgeCommand> responseObserver) {
      return io.grpc.stub.ServerCalls.asyncUnimplementedStreamingCall(getStreamAnalysisResultsMethod(), responseObserver);
    }

    /**
     * <pre>
     * 高优先级告警上报 (需ACK确认)
     * </pre>
     */
    default void reportAlert(com.waterquality.proto.AlertRequest request,
        io.grpc.stub.StreamObserver<com.waterquality.proto.AlertResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getReportAlertMethod(), responseObserver);
    }

    /**
     * <pre>
     * 模型版本检查
     * </pre>
     */
    default void getModelVersion(com.waterquality.proto.ModelVersionRequest request,
        io.grpc.stub.StreamObserver<com.waterquality.proto.ModelVersionResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetModelVersionMethod(), responseObserver);
    }

    /**
     * <pre>
     * 健康检查
     * </pre>
     */
    default void healthCheck(com.waterquality.proto.HealthCheckRequest request,
        io.grpc.stub.StreamObserver<com.waterquality.proto.HealthCheckResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getHealthCheckMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service WaterQualityEdgeService.
   */
  public static abstract class WaterQualityEdgeServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return WaterQualityEdgeServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service WaterQualityEdgeService.
   */
  public static final class WaterQualityEdgeServiceStub
      extends io.grpc.stub.AbstractAsyncStub<WaterQualityEdgeServiceStub> {
    private WaterQualityEdgeServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected WaterQualityEdgeServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new WaterQualityEdgeServiceStub(channel, callOptions);
    }

    /**
     * <pre>
     * 双向流: 边缘端持续上报分析结果，云端下发指令/ACK
     * </pre>
     */
    public io.grpc.stub.StreamObserver<com.waterquality.proto.WaterQualityMessage> streamAnalysisResults(
        io.grpc.stub.StreamObserver<com.waterquality.proto.EdgeCommand> responseObserver) {
      return io.grpc.stub.ClientCalls.asyncBidiStreamingCall(
          getChannel().newCall(getStreamAnalysisResultsMethod(), getCallOptions()), responseObserver);
    }

    /**
     * <pre>
     * 高优先级告警上报 (需ACK确认)
     * </pre>
     */
    public void reportAlert(com.waterquality.proto.AlertRequest request,
        io.grpc.stub.StreamObserver<com.waterquality.proto.AlertResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getReportAlertMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * 模型版本检查
     * </pre>
     */
    public void getModelVersion(com.waterquality.proto.ModelVersionRequest request,
        io.grpc.stub.StreamObserver<com.waterquality.proto.ModelVersionResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetModelVersionMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * 健康检查
     * </pre>
     */
    public void healthCheck(com.waterquality.proto.HealthCheckRequest request,
        io.grpc.stub.StreamObserver<com.waterquality.proto.HealthCheckResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getHealthCheckMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service WaterQualityEdgeService.
   */
  public static final class WaterQualityEdgeServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<WaterQualityEdgeServiceBlockingStub> {
    private WaterQualityEdgeServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected WaterQualityEdgeServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new WaterQualityEdgeServiceBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * 高优先级告警上报 (需ACK确认)
     * </pre>
     */
    public com.waterquality.proto.AlertResponse reportAlert(com.waterquality.proto.AlertRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getReportAlertMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * 模型版本检查
     * </pre>
     */
    public com.waterquality.proto.ModelVersionResponse getModelVersion(com.waterquality.proto.ModelVersionRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetModelVersionMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * 健康检查
     * </pre>
     */
    public com.waterquality.proto.HealthCheckResponse healthCheck(com.waterquality.proto.HealthCheckRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getHealthCheckMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service WaterQualityEdgeService.
   */
  public static final class WaterQualityEdgeServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<WaterQualityEdgeServiceFutureStub> {
    private WaterQualityEdgeServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected WaterQualityEdgeServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new WaterQualityEdgeServiceFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * 高优先级告警上报 (需ACK确认)
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.waterquality.proto.AlertResponse> reportAlert(
        com.waterquality.proto.AlertRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getReportAlertMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * 模型版本检查
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.waterquality.proto.ModelVersionResponse> getModelVersion(
        com.waterquality.proto.ModelVersionRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetModelVersionMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * 健康检查
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.waterquality.proto.HealthCheckResponse> healthCheck(
        com.waterquality.proto.HealthCheckRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getHealthCheckMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_REPORT_ALERT = 0;
  private static final int METHODID_GET_MODEL_VERSION = 1;
  private static final int METHODID_HEALTH_CHECK = 2;
  private static final int METHODID_STREAM_ANALYSIS_RESULTS = 3;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final AsyncService serviceImpl;
    private final int methodId;

    MethodHandlers(AsyncService serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_REPORT_ALERT:
          serviceImpl.reportAlert((com.waterquality.proto.AlertRequest) request,
              (io.grpc.stub.StreamObserver<com.waterquality.proto.AlertResponse>) responseObserver);
          break;
        case METHODID_GET_MODEL_VERSION:
          serviceImpl.getModelVersion((com.waterquality.proto.ModelVersionRequest) request,
              (io.grpc.stub.StreamObserver<com.waterquality.proto.ModelVersionResponse>) responseObserver);
          break;
        case METHODID_HEALTH_CHECK:
          serviceImpl.healthCheck((com.waterquality.proto.HealthCheckRequest) request,
              (io.grpc.stub.StreamObserver<com.waterquality.proto.HealthCheckResponse>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_STREAM_ANALYSIS_RESULTS:
          return (io.grpc.stub.StreamObserver<Req>) serviceImpl.streamAnalysisResults(
              (io.grpc.stub.StreamObserver<com.waterquality.proto.EdgeCommand>) responseObserver);
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getStreamAnalysisResultsMethod(),
          io.grpc.stub.ServerCalls.asyncBidiStreamingCall(
            new MethodHandlers<
              com.waterquality.proto.WaterQualityMessage,
              com.waterquality.proto.EdgeCommand>(
                service, METHODID_STREAM_ANALYSIS_RESULTS)))
        .addMethod(
          getReportAlertMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.waterquality.proto.AlertRequest,
              com.waterquality.proto.AlertResponse>(
                service, METHODID_REPORT_ALERT)))
        .addMethod(
          getGetModelVersionMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.waterquality.proto.ModelVersionRequest,
              com.waterquality.proto.ModelVersionResponse>(
                service, METHODID_GET_MODEL_VERSION)))
        .addMethod(
          getHealthCheckMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.waterquality.proto.HealthCheckRequest,
              com.waterquality.proto.HealthCheckResponse>(
                service, METHODID_HEALTH_CHECK)))
        .build();
  }

  private static abstract class WaterQualityEdgeServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    WaterQualityEdgeServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.waterquality.proto.WaterQualityMessageOuterClass.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("WaterQualityEdgeService");
    }
  }

  private static final class WaterQualityEdgeServiceFileDescriptorSupplier
      extends WaterQualityEdgeServiceBaseDescriptorSupplier {
    WaterQualityEdgeServiceFileDescriptorSupplier() {}
  }

  private static final class WaterQualityEdgeServiceMethodDescriptorSupplier
      extends WaterQualityEdgeServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final String methodName;

    WaterQualityEdgeServiceMethodDescriptorSupplier(String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (WaterQualityEdgeServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new WaterQualityEdgeServiceFileDescriptorSupplier())
              .addMethod(getStreamAnalysisResultsMethod())
              .addMethod(getReportAlertMethod())
              .addMethod(getGetModelVersionMethod())
              .addMethod(getHealthCheckMethod())
              .build();
        }
      }
    }
    return result;
  }
}
