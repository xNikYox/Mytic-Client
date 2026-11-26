package de.myticlegacy.client.render;

import de.myticlegacy.client.cosmetics.CosmeticsCore;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;

import java.util.HashMap;
import java.util.Map;

/**
 * Modellteile der Accessoires „Kopf & Hals“ aus den Boxen im Katalog (gleich für alle Versionen ab 1.21). Die Boxen
 * liegen in Texel-Einheiten (2 je Modell-Pixel, damit die Textur-Ecken ganzzahlig sind) – beim Zeichnen auf die Hälfte
 * verkleinern. Drehpunkt ist der Hals (wie Kopf und Oberkörper des Spieler-Modells).
 */
public final class AccModels {
    /** je Accessoire {normale Boxen, leuchtende Boxen}; ein Eintrag ist null, wenn es keine solchen Boxen gibt */
    private static final Map<String, ModelPart[]> PARTS = new HashMap<>();

    private AccModels() {
    }

    /** Nur Render-Thread. */
    public static ModelPart[] parts(CosmeticsCore.Acc acc) {
        return PARTS.computeIfAbsent(acc.id, id -> new ModelPart[]{build(acc, false), build(acc, true)});
    }

    private static ModelPart build(CosmeticsCore.Acc acc, boolean emissive) {
        MeshDefinition mesh = new MeshDefinition();
        int n = 0;
        for (CosmeticsCore.AccBox b : acc.boxes) {
            if (b.emissive != emissive) continue;
            float[] m = b.minecraft();
            CubeListBuilder cube = CubeListBuilder.create().texOffs(b.u, b.v).mirror(b.mirror)
                    .addBox(m[0], m[1], m[2], b.tw, b.th, b.td, new CubeDeformation(m[3], m[4], m[5]));
            mesh.getRoot().addOrReplaceChild("b" + n++, cube, PartPose.ZERO);
        }
        return n == 0 ? null : LayerDefinition.create(mesh, acc.texW, acc.texH).bakeRoot();
    }
}
