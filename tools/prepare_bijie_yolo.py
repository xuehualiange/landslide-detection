import argparse
import random
import shutil
from pathlib import Path

import cv2
import numpy as np


def ensure_dir(path: Path):
    path.mkdir(parents=True, exist_ok=True)


def mask_to_yolo_labels(mask_path: Path):
    mask = cv2.imread(str(mask_path), cv2.IMREAD_GRAYSCALE)
    if mask is None:
        return [], None, None

    h, w = mask.shape[:2]
    _, binary = cv2.threshold(mask, 1, 255, cv2.THRESH_BINARY)
    contours, _ = cv2.findContours(binary, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)

    labels = []
    for contour in contours:
        x, y, bw, bh = cv2.boundingRect(contour)
        area = bw * bh
        if area < 25:
            continue

        x_center = (x + bw / 2) / w
        y_center = (y + bh / 2) / h
        nw = bw / w
        nh = bh / h

        labels.append(f"0 {x_center:.6f} {y_center:.6f} {nw:.6f} {nh:.6f}")

    return labels, w, h


def split_items(items, train_ratio=0.7, val_ratio=0.2, seed=42):
    random.Random(seed).shuffle(items)
    n = len(items)
    n_train = int(n * train_ratio)
    n_val = int(n * val_ratio)

    train = items[:n_train]
    val = items[n_train:n_train + n_val]
    test = items[n_train + n_val:]
    return train, val, test


def copy_with_label(samples, split, out_root: Path):
    img_dir = out_root / "images" / split
    lab_dir = out_root / "labels" / split
    ensure_dir(img_dir)
    ensure_dir(lab_dir)

    for sample in samples:
        src_img = sample["img"]
        dst_img = img_dir / src_img.name
        shutil.copy2(src_img, dst_img)

        label_lines = sample["labels"]
        txt_name = src_img.stem + ".txt"
        (lab_dir / txt_name).write_text("\n".join(label_lines), encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description="Convert Bijie landslide dataset to YOLO detection format")
    parser.add_argument("--src", required=True, help="Bijie dataset root, e.g. E:/Bijie_landslide_dataset/Bijie-landslide-dataset")
    parser.add_argument("--out", required=True, help="Output YOLO dataset root, e.g. E:/datasets/landslide-yolo")
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()

    src = Path(args.src)
    out = Path(args.out)

    landslide_img_dir = src / "landslide" / "image"
    landslide_mask_dir = src / "landslide" / "mask"
    non_img_dir = src / "non-landslide" / "image"

    if not landslide_img_dir.exists() or not landslide_mask_dir.exists() or not non_img_dir.exists():
        raise SystemExit("输入目录结构不正确，请确认包含 landslide/image, landslide/mask, non-landslide/image")

    samples = []

    # Landslide positives
    for img_path in sorted(landslide_img_dir.glob("*.png")):
        mask_path = landslide_mask_dir / img_path.name
        if not mask_path.exists():
            continue
        labels, _, _ = mask_to_yolo_labels(mask_path)
        if not labels:
            continue
        samples.append({"img": img_path, "labels": labels, "type": "pos"})

    # Non-landslide negatives (empty label files)
    for img_path in sorted(non_img_dir.glob("*.png")):
        samples.append({"img": img_path, "labels": [], "type": "neg"})

    pos = [s for s in samples if s["type"] == "pos"]
    neg = [s for s in samples if s["type"] == "neg"]

    pos_train, pos_val, pos_test = split_items(pos, seed=args.seed)
    neg_train, neg_val, neg_test = split_items(neg, seed=args.seed)

    train = pos_train + neg_train
    val = pos_val + neg_val
    test = pos_test + neg_test

    random.Random(args.seed).shuffle(train)
    random.Random(args.seed).shuffle(val)
    random.Random(args.seed).shuffle(test)

    copy_with_label(train, "train", out)
    copy_with_label(val, "val", out)
    copy_with_label(test, "test", out)

    yaml_text = f"""path: {out.as_posix()}\ntrain: images/train\nval: images/val\ntest: images/test\n\nnames:\n  0: landslide\n"""
    (out / "landslide.yaml").write_text(yaml_text, encoding="utf-8")

    print("=== Done ===")
    print(f"Output: {out}")
    print(f"Train: {len(train)} | Val: {len(val)} | Test: {len(test)}")
    print(f"Positives: {len(pos)} | Negatives: {len(neg)}")
    print(f"YAML: {out / 'landslide.yaml'}")


if __name__ == "__main__":
    main()
