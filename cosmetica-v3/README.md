# Cosmetica v0.4 (Fabric 1.20.4)
Build: `gradle build` (Java 17). Press K in-game.
Slots: head, suit, back, hand, aura, pet (mascota).

## Novedades v0.4
- **Mascotas** (slot `pet`): siguen al jugador. Incluye Slime, Gatito y Fantasmita. Modo `ground` (camina) o `float` (flota).
- **Anadir modelo**: boton "+ Anadir modelo" en el menu (K). Lista los .bbmodel de Descargas/Escritorio/`cosmetica_import`,
  tambien puedes arrastrar el archivo a la ventana. Elige slot, tamano, altura y pulsa "Guardar y equipar".
- **Auras arregladas**: ahora usan un sistema de particulas propio (no el ParticleManager de vanilla) y se ven tambien en la vista previa del menu.

## Modelos de Blockbench a mano
Pon el .bbmodel (o un .json Java Block + .png) en `config/cosmetica/models/`. Prefijo = slot: `head_`, `back_`, `hand_`, `pet_`.
Ajustes opcionales en `NOMBRE.cosmetic.json`: `{"name","slot","scale","offset":[x,y,z],"yaw":180}`.
Para mascotas el modelo debe tener los pies en Y=0 en Blockbench y mirar al norte (-Z).

## Mascotas en JSON (assets/cosmetica/cosmetics/*.json)
`"slot":"pet","pet":{"mode":"ground|float","scale":1.0}`, `"parts":[{"anchor":"root","boxes":[...]}]`.
Coordenadas como un modelo de entidad vanilla: pies en y=24. Cajas animables: `swing` (grados), `phase` (radianes), `idle` (sigue moviendose quieto), `pivot`, `rotate`.
