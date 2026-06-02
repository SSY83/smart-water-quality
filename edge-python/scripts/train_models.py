"""
水质监测深度学习模型训练与导出脚本

生成两个 TFLite 模型:
1. mobilenet_v2_quantized.tflite - MobileNetV2 多分类器 (浊度+污染类型)
2. unet_quantized.tflite - U-Net 水质分割模型

使用合成数据训练，可用真实数据集替换 generate_synthetic_image() 函数。
"""

import os
import sys
import argparse
import logging
from typing import Callable, Tuple

import numpy as np

os.environ['TF_CPP_MIN_LOG_LEVEL'] = '2'
logging.basicConfig(level=logging.INFO, format='%(asctime)s [%(levelname)s] %(message)s')
logger = logging.getLogger(__name__)


# ============================================================
# 合成数据生成 (用真实数据替换此函数)
# ============================================================

def _perlin_noise_2d(shape, res, seed=0):
    """Simple upsampled noise for synthetic water textures"""
    rng = np.random.RandomState(seed)
    small = rng.rand(res[0], res[1]).astype(np.float32)

    # Bilinear upscale to target shape
    h, w = shape
    rh, rw = res
    y_coords = np.linspace(0, rh - 1, h)
    x_coords = np.linspace(0, rw - 1, w)
    y0 = np.floor(y_coords).astype(int)
    x0 = np.floor(x_coords).astype(int)
    y1 = np.minimum(y0 + 1, rh - 1)
    x1 = np.minimum(x0 + 1, rw - 1)
    wy = (y_coords - y0)[:, np.newaxis]
    wx = (x_coords - x0)[np.newaxis, :]

    result = (small[y0[:, np.newaxis], x0[np.newaxis, :]] * (1 - wy) * (1 - wx) +
              small[y1[:, np.newaxis], x0[np.newaxis, :]] * wy * (1 - wx) +
              small[y0[:, np.newaxis], x1[np.newaxis, :]] * (1 - wy) * wx +
              small[y1[:, np.newaxis], x1[np.newaxis, :]] * wy * wx)
    return result.astype(np.float32)


def generate_synthetic_image(turbidity_level: int, pollution_type: int,
                             size: Tuple[int, int] = (224, 224)) -> np.ndarray:
    """
    生成合成水面图像。用真实摄像头图像替换此函数。

    Args:
        turbidity_level: 0=清澈, 1=轻度浑浊, 2=中度浑浊, 3=重度浑浊
        pollution_type: 0=有机污染, 1=氮磷污染, 2=油脂污染, 3=微塑料
        size: 输出尺寸

    Returns: RGB 图像 (H, W, 3), float32, 范围 [0, 1]
    """
    h, w = size
    seed_base = turbidity_level * 100 + pollution_type * 10

    # 水面基底纹理
    water_base = _perlin_noise_2d((h, w), (4, 4), seed=seed_base)

    # 水色基底: 蓝绿色
    r = np.full((h, w), 0.05, dtype=np.float32)
    g = np.full((h, w), 0.30, dtype=np.float32)
    b = np.full((h, w), 0.45, dtype=np.float32)

    # 波纹高光
    ripple = _perlin_noise_2d((h, w), (8, 8), seed=seed_base + 1)
    highlight = np.clip(ripple * 0.15, 0, 0.2)
    r += highlight
    g += highlight
    b += highlight

    # 浊度叠加: 棕色/灰色，随浊度等级递增
    if turbidity_level > 0:
        turbidity_strength = [0, 0.25, 0.50, 0.75][turbidity_level]
        turbidity_noise = _perlin_noise_2d((h, w), (6, 6), seed=seed_base + 2)
        turbidity_mask = turbidity_noise > 0.3

        r[turbidity_mask] += turbidity_strength * 0.4
        g[turbidity_mask] += turbidity_strength * 0.25
        b[turbidity_mask] -= turbidity_strength * 0.2

        # 重度浑浊有更大块区域
        turbidity_blob = _perlin_noise_2d((h, w), (2, 2), seed=seed_base + 5)
        r += turbidity_blob * turbidity_strength * 0.3
        g += turbidity_blob * turbidity_strength * 0.15

    # 污染类型特征
    if pollution_type == 0:  # 有机污染 - 绿色调
        organic = _perlin_noise_2d((h, w), (5, 5), seed=seed_base + 3)
        g[organic > 0.5] += 0.2
        r[organic > 0.5] -= 0.05
    elif pollution_type == 1:  # 氮磷污染 - 藻类绿+浑浊
        algae = _perlin_noise_2d((h, w), (3, 3), seed=seed_base + 3)
        r[algae > 0.4] += 0.1
        g[algae > 0.4] += 0.35
        b[algae > 0.4] -= 0.1
    elif pollution_type == 2:  # 油脂污染 - 彩虹光泽
        oil = _perlin_noise_2d((h, w), (7, 7), seed=seed_base + 3)
        oil_ring = (oil * 12) % 1.0
        spectrum = np.stack([
            np.sin(oil_ring * np.pi + 0),
            np.sin(oil_ring * np.pi + 2.1),
            np.sin(oil_ring * np.pi + 4.2)
        ], axis=-1)
        r += spectrum[:, :, 0] * 0.15
        g += spectrum[:, :, 1] * 0.15
        b += spectrum[:, :, 2] * 0.15
    elif pollution_type == 3:  # 微塑料 - 白色斑点
        speckle = np.random.RandomState(seed_base + 3)
        speckle_mask = speckle.rand(h, w) > 0.97
        r[speckle_mask] += 0.5
        g[speckle_mask] += 0.5
        b[speckle_mask] += 0.5

    # 气泡/浮沫
    foam = np.random.RandomState(seed_base + 4)
    foam_mask = foam.rand(h, w) > 0.95
    r[foam_mask] += 0.3
    g[foam_mask] += 0.3
    b[foam_mask] += 0.3

    # 合成并裁剪到 [0, 1]
    image = np.stack([r, g, b], axis=-1).astype(np.float32)
    image = np.clip(image, 0.02, 0.95)

    # 添加随机高斯噪声模拟传感器噪声
    image += np.random.randn(h, w, 3).astype(np.float32) * 0.02
    image = np.clip(image, 0.0, 1.0)

    return image


def generate_synthetic_mask(turbidity_level: int, pollution_type: int,
                            size: Tuple[int, int] = (224, 224),
                            num_classes: int = 5) -> np.ndarray:
    """
    生成分割掩码。
    Class 0 = 背景/正常水面
    Class 1 = 浑浊区域
    Class 2 = 有机污染区域
    Class 3 = 油脂/化学污染区域
    Class 4 = 微塑料/悬浮物区域
    """
    h, w = size
    seed_base = turbidity_level * 100 + pollution_type * 10
    mask = np.zeros((h, w), dtype=np.uint8)

    # 浑浊区域 (class 1): 随 turbidity_level 增大
    if turbidity_level > 0:
        turbidity_noise = _perlin_noise_2d((h, w), (6, 6), seed=seed_base + 2)
        turbidity_threshold = {1: 0.55, 2: 0.35, 3: 0.15}[turbidity_level]
        mask[turbidity_noise > turbidity_threshold] = 1

    # 污染区域 (class 2/3/4): 基于污染类型
    if pollution_type in (0, 1):
        pollution_class = 2  # 有机/氮磷 → class 2
    elif pollution_type == 2:
        pollution_class = 3  # 油脂 → class 3
    else:
        pollution_class = 4  # 微塑料 → class 4

    pollution_noise = _perlin_noise_2d((h, w), (4, 4), seed=seed_base + 3)
    pollution_threshold = 0.5
    if turbidity_level >= 2:
        pollution_threshold = 0.35  # 高浊度时污染区域更大
    mask[pollution_noise > pollution_threshold] = pollution_class

    return mask


def create_synthetic_dataset(num_samples: int = 2000,
                             num_classes: int = 5) -> Tuple[np.ndarray, np.ndarray, np.ndarray]:
    """生成合成训练数据集"""
    logger.info("Generating %d synthetic samples...", num_samples)
    X = np.zeros((num_samples, 224, 224, 3), dtype=np.float32)
    y_cls = np.zeros((num_samples, 8), dtype=np.float32)
    y_seg = np.zeros((num_samples, 224, 224), dtype=np.uint8)

    for i in range(num_samples):
        t_level = np.random.randint(0, 4)
        p_type = np.random.randint(0, 4)

        X[i] = generate_synthetic_image(t_level, p_type)
        y_seg[i] = generate_synthetic_mask(t_level, p_type, num_classes=num_classes)

        # One-hot 编码: [0:4] = 浊度, [4:8] = 污染类型
        y_cls[i, t_level] = 1.0
        y_cls[i, 4 + p_type] = 1.0

        if (i + 1) % 500 == 0:
            logger.info("  %d/%d samples generated", i + 1, num_samples)

    logger.info("Dataset: X=%s, y_cls=%s, y_seg=%s", X.shape, y_cls.shape, y_seg.shape)
    return X, y_cls, y_seg


# ============================================================
# 模型构建
# ============================================================

def build_mobilenet_classifier(num_turbidity: int = 4,
                               num_pollution: int = 4) -> "tf.keras.Model":
    """构建双头 MobileNetV2 分类器"""
    import tensorflow as tf

    base = tf.keras.applications.MobileNetV2(
        input_shape=(224, 224, 3),
        include_top=False,
        weights='imagenet'
    )
    base.trainable = False

    x = base.output
    x = tf.keras.layers.GlobalAveragePooling2D()(x)
    x = tf.keras.layers.Dropout(0.2)(x)
    x = tf.keras.layers.Dense(128, activation='relu')(x)
    x = tf.keras.layers.Dropout(0.1)(x)

    turb_head = tf.keras.layers.Dense(num_turbidity, name='turbidity_logits')(x)
    poll_head = tf.keras.layers.Dense(num_pollution, name='pollution_logits')(x)

    # 训练时输出 logits (from_logits=True 在 loss 中)
    output = tf.keras.layers.Concatenate(name='combined_logits')([turb_head, poll_head])
    return tf.keras.Model(inputs=base.input, outputs=output, name='mobilenet_classifier')


def build_export_classifier(trained_model: "tf.keras.Model") -> "tf.keras.Model":
    """为 TFLite 导出构建包装模型，添加 softmax"""
    import tensorflow as tf

    logits = trained_model.output
    # Keras 3: 使用 keras.ops.softmax 而非 tf.nn.softmax
    turb_probs = tf.keras.layers.Softmax(name='turbidity_softmax')(logits[:, :4])
    poll_probs = tf.keras.layers.Softmax(name='pollution_softmax')(logits[:, 4:8])

    output = tf.keras.layers.Concatenate(name='combined_probs')([turb_probs, poll_probs])
    return tf.keras.Model(inputs=trained_model.input, outputs=output,
                          name='mobilenet_classifier_export')


def build_unet_segmenter(num_classes: int = 5,
                         input_shape: Tuple[int, int, int] = (224, 224, 3)) -> "tf.keras.Model":
    """构建 U-Net，使用 MobileNetV2 作为编码器"""
    import tensorflow as tf

    base = tf.keras.applications.MobileNetV2(
        input_shape=input_shape,
        include_top=False,
        weights='imagenet'
    )

    # 编码器层 (skip connections) — 基于 Keras 3 / TF 2.21 MobileNetV2
    layer_names = [
        'block_1_expand_relu',   # 112x112x96
        'block_3_expand_relu',   # 56x56x144
        'block_6_expand_relu',   # 28x28x192
        'block_13_expand_relu',  # 14x14x576
    ]
    encoder_layers = [base.get_layer(name).output for name in layer_names]

    # Bottleneck: out_relu at 7x7x1280
    bottleneck = base.get_layer('out_relu').output

    # 解码器: 7→14→28→56→112→224 (5 次上采样，4 个 skip connection)
    inputs = base.input
    x = bottleneck  # 7x7x1280

    skip_layers = list(reversed(encoder_layers))  # 14, 28, 56, 112
    skip_filters = [576, 192, 144, 96]

    for skip, f in zip(skip_layers, skip_filters):
        x = tf.keras.layers.Conv2DTranspose(f, 3, strides=2, padding='same')(x)
        x = tf.keras.layers.BatchNormalization()(x)
        x = tf.keras.layers.ReLU()(x)
        x = tf.keras.layers.Concatenate()([x, skip])
        x = tf.keras.layers.Conv2D(f, 3, padding='same')(x)
        x = tf.keras.layers.BatchNormalization()(x)
        x = tf.keras.layers.ReLU()(x)

    # 最终上采样: 112→224
    x = tf.keras.layers.Conv2DTranspose(64, 3, strides=2, padding='same')(x)
    x = tf.keras.layers.BatchNormalization()(x)
    x = tf.keras.layers.ReLU()(x)

    # 输出: 每个像素的类别 logits (model_loader.py 会做 argmax)
    output = tf.keras.layers.Conv2D(num_classes, 1, activation=None,
                                    name='segmentation_logits')(x)
    return tf.keras.Model(inputs=inputs, outputs=output, name='unet_segmenter')


# ============================================================
# 训练
# ============================================================

def train_mobilenet(model, X_train, y_train, X_val, y_val,
                    epochs: int = 20, batch_size: int = 32):
    """训练分类器 (仅训练头部层, 编码器冻结)"""
    import tensorflow as tf

    model.compile(
        optimizer=tf.keras.optimizers.Adam(1e-3),
        loss=tf.keras.losses.CategoricalCrossentropy(from_logits=True),
        metrics=['accuracy']
    )

    logger.info("Training MobileNetV2 classifier (%d epochs)...", epochs)
    model.fit(X_train, y_train,
              validation_data=(X_val, y_val),
              epochs=epochs,
              batch_size=batch_size,
              verbose=1)
    return model


def train_unet(model, X_train, y_train_masks, X_val, y_val_masks,
               epochs: int = 30, batch_size: int = 16, num_classes: int = 5):
    """训练 U-Net 分割模型"""
    import tensorflow as tf

    # One-hot encode masks
    y_train_oh = tf.keras.utils.to_categorical(y_train_masks, num_classes)
    y_val_oh = tf.keras.utils.to_categorical(y_val_masks, num_classes)

    model.compile(
        optimizer=tf.keras.optimizers.Adam(1e-3),
        loss=tf.keras.losses.CategoricalCrossentropy(from_logits=True),
        metrics=['accuracy']
    )

    logger.info("Training U-Net segmenter (%d epochs)...", epochs)
    model.fit(X_train, y_train_oh,
              validation_data=(X_val, y_val_oh),
              epochs=epochs,
              batch_size=batch_size,
              verbose=1)
    return model


# ============================================================
# TFLite 导出
# ============================================================

def representative_dataset_gen() -> Callable:
    """INT8 量化校准数据集生成器"""
    def _gen():
        for i in range(100):
            t = i % 4
            p = (i // 4) % 4
            img = generate_synthetic_image(t, p)
            yield [np.expand_dims(img.astype(np.float32), axis=0)]
    return _gen


def export_to_tflite(model: "tf.keras.Model", output_path: str,
                     quantize_int8: bool = True) -> bool:
    """导出 Keras 模型为 TFLite"""
    import tensorflow as tf

    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]

    if quantize_int8:
        try:
            converter.representative_dataset = representative_dataset_gen()
            converter.target_spec.supported_ops = [
                tf.lite.OpsSet.TFLITE_BUILTINS_INT8
            ]
            converter.inference_input_type = tf.float32
            converter.inference_output_type = tf.float32
            logger.info("  Using INT8 quantization with float I/O")
        except Exception as e:
            logger.warning("  INT8 config failed (%s), using float16 fallback", e)
            quantize_int8 = False

    if not quantize_int8:
        converter.target_spec.supported_types = [tf.float16]

    try:
        tflite_model = converter.convert()
    except Exception as e:
        logger.warning("  Full INT8 conversion failed (%s), trying dynamic range", e)
        converter = tf.lite.TFLiteConverter.from_keras_model(model)
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        tflite_model = converter.convert()

    os.makedirs(os.path.dirname(output_path) or '.', exist_ok=True)
    with open(output_path, 'wb') as f:
        f.write(tflite_model)

    size_mb = len(tflite_model) / (1024 * 1024)
    logger.info("  Exported: %s (%.2f MB)", output_path, size_mb)
    return True


# ============================================================
# 验证
# ============================================================

def verify_model(tflite_path: str,
                 expected_input_shape: Tuple,
                 expected_output_info: dict) -> bool:
    """
    验证导出的 TFLite 模型。

    Args:
        tflite_path: .tflite 文件路径
        expected_input_shape: 期望输入形状 (例如 (1, 224, 224, 3))
        expected_output_info: 'classifier' → 检查 (1, 8),
                              'segmenter' → 检查 argmax 后 (224, 224)
    """
    import tensorflow as tf

    logger.info("Verifying: %s", os.path.basename(tflite_path))

    interpreter = tf.lite.Interpreter(model_path=tflite_path)
    interpreter.allocate_tensors()

    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()

    # 验证输入形状
    input_shape = tuple(input_details[0]['shape'])
    if input_shape != expected_input_shape:
        logger.error("  Input shape mismatch: got %s, expected %s",
                     input_shape, expected_input_shape)
        return False

    # 验证 dtype 检测 (模拟 model_loader.py 的 uint8 检测)
    is_quantized = input_details[0]['dtype'] == np.uint8
    logger.info("  Input dtype: %s (quantized=%s)", input_details[0]['dtype'], is_quantized)

    # 运行一次推理
    sample_input = np.random.randn(*expected_input_shape).astype(np.float32)
    interpreter.set_tensor(input_details[0]['index'], sample_input)
    interpreter.invoke()
    output = interpreter.get_tensor(output_details[0]['index'])

    output_shape = tuple(output.shape)
    logger.info("  Output shape: %s, dtype: %s", output_shape, output.dtype)

    # 分类器输出验证
    if expected_output_info.get('type') == 'classifier':
        expected = expected_output_info.get('shape')
        if expected and output_shape != expected:
            logger.error("  Output shape mismatch: got %s, expected %s",
                         output_shape, expected)
            return False
        # 验证 argmax 和切片能正常工作 (模拟 model_loader.py lines 113-115)
        turbidity_level = int(np.argmax(output[0, :4]))
        confidence = float(np.max(output[0, :4]))
        pollution_probs = output[0, 4:8].tolist()
        logger.info("  turbidity_level=%d, confidence=%.4f, pollution_probs=%s",
                    turbidity_level, confidence, pollution_probs)
        if not (0 <= turbidity_level <= 3):
            logger.error("  turbidity_level out of range: %d", turbidity_level)
            return False
        if not (0.0 <= confidence <= 1.0 + 1e-6):
            logger.warning("  confidence out of [0,1]: %.4f", confidence)

    # 分割模型输出验证
    elif expected_output_info.get('type') == 'segmenter':
        mask = np.argmax(output[0], axis=-1)
        logger.info("  Mask shape after argmax: %s, dtype: %s", mask.shape, mask.dtype)
        expected_mask = expected_output_info.get('mask_shape')
        if expected_mask and mask.shape != expected_mask:
            logger.error("  Mask shape mismatch: got %s, expected %s",
                         mask.shape, expected_mask)
            return False

    logger.info("  Verification PASSED")
    return True


# ============================================================
# 主入口
# ============================================================

def main():
    parser = argparse.ArgumentParser(
        description='水质监测深度学习模型训练与导出')
    parser.add_argument('--output-dir', default='models',
                        help='模型输出目录 (默认: models)')
    parser.add_argument('--synthetic-samples', type=int, default=2000,
                        help='合成数据样本数 (默认: 2000)')
    parser.add_argument('--epochs-classifier', type=int, default=20,
                        help='分类器训练轮数 (默认: 20)')
    parser.add_argument('--epochs-segmenter', type=int, default=30,
                        help='分割模型训练轮数 (默认: 30)')
    parser.add_argument('--skip-training', action='store_true',
                        help='跳过训练，直接从未训练模型导出 (快速测试)')
    parser.add_argument('--no-quantize', action='store_true',
                        help='跳过 INT8 量化')
    args = parser.parse_args()

    output_dir = os.path.abspath(args.output_dir)
    os.makedirs(output_dir, exist_ok=True)

    logger.info("=" * 60)
    logger.info("水质监测模型训练流水线")
    logger.info("输出目录: %s", output_dir)
    logger.info("合成样本: %d", args.synthetic_samples)
    logger.info("=" * 60)

    # ---- Step 1: 生成数据集 ----
    X, y_cls, y_seg = create_synthetic_dataset(args.synthetic_samples)

    # 80/20 分割
    split = int(len(X) * 0.8)
    X_train, X_val = X[:split], X[split:]
    y_cls_train, y_cls_val = y_cls[:split], y_cls[split:]
    y_seg_train, y_seg_val = y_seg[:split], y_seg[split:]

    # 归一化到 [-1, 1] (匹配 ImageNet 预处理)
    X_train_norm = (X_train - 0.5) * 2.0
    X_val_norm = (X_val - 0.5) * 2.0

    # ---- Step 2: 构建模型 ----
    logger.info("Building models...")
    mobilenet = build_mobilenet_classifier()
    logger.info("  MobileNetV2 classifier: input=%s, output=%s",
                mobilenet.input_shape, mobilenet.output_shape)
    unet = build_unet_segmenter()
    logger.info("  U-Net segmenter: input=%s, output=%s",
                unet.input_shape, unet.output_shape)

    # ---- Step 3: 训练 ----
    if not args.skip_training:
        mobilenet = train_mobilenet(mobilenet, X_train_norm, y_cls_train,
                                    X_val_norm, y_cls_val,
                                    epochs=args.epochs_classifier)
        unet = train_unet(unet, X_train_norm, y_seg_train,
                          X_val_norm, y_seg_val,
                          epochs=args.epochs_segmenter)
    else:
        logger.info("Skipping training (--skip-training)")

    # ---- Step 4: 导出 TFLite ----
    logger.info("Exporting models to TFLite...")

    # 分类器: 构建带 softmax 的导出模型 (确保输出兼容 model_loader.py)
    mobilenet_export = build_export_classifier(mobilenet)

    mobilenet_path = os.path.join(output_dir, 'mobilenet_v2_quantized.tflite')
    export_to_tflite(mobilenet_export, mobilenet_path,
                     quantize_int8=not args.no_quantize)

    unet_path = os.path.join(output_dir, 'unet_quantized.tflite')
    export_to_tflite(unet, unet_path, quantize_int8=not args.no_quantize)

    # ---- Step 5: 验证 ----
    logger.info("Verifying exported models...")

    ok_m = verify_model(mobilenet_path,
                        expected_input_shape=(1, 224, 224, 3),
                        expected_output_info={'type': 'classifier', 'shape': (1, 8)})

    ok_u = verify_model(unet_path,
                        expected_input_shape=(1, 224, 224, 3),
                        expected_output_info={'type': 'segmenter',
                                              'mask_shape': (224, 224)})

    # ---- Done ----
    logger.info("=" * 60)
    if ok_m and ok_u:
        logger.info("All models exported and verified successfully!")
    else:
        logger.error("Verification failed for one or more models")
        sys.exit(1)
    logger.info("  %s (%s)", mobilenet_path, "OK" if ok_m else "FAIL")
    logger.info("  %s (%s)", unet_path, "OK" if ok_u else "FAIL")
    logger.info("=" * 60)


if __name__ == '__main__':
    main()
