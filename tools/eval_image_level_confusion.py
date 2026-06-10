"""Image-level confusion matrix - all 4 cells including TN (true bg, pred bg)."""
from __future__ import annotations
import argparse
from pathlib import Path
import matplotlib.pyplot as plt
import numpy as np
import yaml
from ultralytics import YOLO

plt.rcParams["font.sans-serif"] = ["Microsoft YaHei", "SimHei", "DejaVu Sans"]
plt.rcParams["axes.unicode_minus"] = False

def load_dataset_root(data_yaml: Path) -> Path:
    cfg = yaml.safe_load(data_yaml.read_text(encoding="utf-8"))
    root = Path(cfg["path"])
    return root if root.is_absolute() else (data_yaml.parent / root).resolve()

def label_has_objects(label_path: Path) -> bool:
    return label_path.is_file() and bool(label_path.read_text(encoding="utf-8").strip())

def collect_images(img_dir: Path) -> list[Path]:
    exts = {".jpg", ".jpeg", ".png", ".bmp", ".tif", ".tiff"}
    return sorted(p for p in img_dir.iterdir() if p.suffix.lower() in exts)

def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("--model", required=True)
    p.add_argument("--data", default="E:/landslide-yolo/landslide.yaml")
    p.add_argument("--split", default="val", choices=["train", "val", "test"])
    p.add_argument("--conf", type=float, default=0.45)
    p.add_argument("--out", default="")
    args = p.parse_args()

    data_yaml = Path(args.data)
    root = load_dataset_root(data_yaml)
    img_dir = root / "images" / args.split
    lab_dir = root / "labels" / args.split
    model = YOLO(args.model)
    images = collect_images(img_dir)

    tn = fp = tp = fn = 0
    for img_path in images:
        has_gt = label_has_objects(lab_dir / f"{img_path.stem}.txt")
        r = model.predict(source=str(img_path), conf=args.conf, verbose=False, save=False)
        n = len(r[0].boxes) if r and r[0].boxes is not None else 0
        if has_gt:
            tp += 1 if n > 0 else 0
            fn += 0 if n > 0 else 1
        else:
            fp += 1 if n > 0 else 0
            tn += 0 if n > 0 else 1

    matrix = np.array([[tp, fp], [fn, tn]], dtype=int)
    labels = ["landslide", "background"]
    out_dir = Path(args.out) if args.out else Path(args.model).resolve().parent.parent
    out_dir.mkdir(parents=True, exist_ok=True)

    for name, data, norm in [
        ("confusion_matrix_image_level.png", matrix, False),
        ("confusion_matrix_image_level_normalized.png", None, True),
    ]:
        fig, ax = plt.subplots(figsize=(8, 6))
        plot_m = data if not norm else np.divide(matrix, matrix.sum(axis=0, keepdims=True), where=matrix.sum(axis=0, keepdims=True) > 0)
        im = ax.imshow(plot_m, cmap="Blues", vmin=0, vmax=1 if norm else None)
        ax.set_xticks([0, 1]); ax.set_yticks([0, 1])
        ax.set_xticklabels(labels); ax.set_yticklabels(labels)
        ax.set_xlabel("True"); ax.set_ylabel("Predicted")
        ax.set_title("Image-level Confusion Matrix" + (" Normalized" if norm else ""))
        for i in range(2):
            for j in range(2):
                t = f"{plot_m[i,j]:.2f}" if norm else str(matrix[i, j])
                ax.text(j, i, t, ha="center", va="center", fontsize=14)
        plt.colorbar(im, ax=ax, fraction=0.046)
        plt.tight_layout()
        fig.savefig(out_dir / name, dpi=150)
        plt.close(fig)

    (out_dir / "confusion_matrix_image_level.csv").write_text(
        f"TP,{tp}\nFN,{fn}\nFP,{fp}\nTN,{tn}\n", encoding="utf-8")
    print(f"TP={tp} FN={fn} FP={fp} TN={tn}")
    print(f"Saved to {out_dir}")

if __name__ == "__main__":
    main()