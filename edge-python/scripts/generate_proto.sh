#!/bin/bash
# Generate Python gRPC stubs from the shared proto file
# Run from the repository root

PROTO_DIR="cloud-backend/src/main/proto"
OUTPUT_DIR="edge-python/src/proto"

mkdir -p "$OUTPUT_DIR"

python -m grpc_tools.protoc \
    -I"$PROTO_DIR" \
    --python_out="$OUTPUT_DIR" \
    --grpc_python_out="$OUTPUT_DIR" \
    "$PROTO_DIR/water_quality_message.proto"

# Create __init__.py for the proto package
touch "$OUTPUT_DIR/__init__.py"

echo "Generated proto stubs in $OUTPUT_DIR"
echo "Files:"
ls -la "$OUTPUT_DIR"
