# VBTM Create Belt Stack Fix

NeoForge 1.21.1 / Create 6.0.x. Two mixins that stop a server crash caused by an oversized item stack on a Create belt.

Create's `BeltInventory` stores whatever `ItemStack` count it is handed and never validates it. Vanilla 1.21's item codec
refuses counts outside `[1;99]`, so the first time such a belt is saved or synced to a client the server thread dies with
`Exception ticking world: Value must be within range [1;99]: N` (seen on the Vanilla But Then More server with a stack of
192 stone). Create's own `IItemHandler` entry points clamp to the max stack size, so the oversized stack comes from a mod
that either builds a >64 `ItemStack` and pushes it through a non-clamping path or grows a live belt stack in place.

What this jar does:

- `BeltInventory.insert` (HEAD): an oversized stack is split into max-stack-size pieces that trail the original on the
  belt, and the producer's stack trace is logged under `vbtm-beltstack-fix` so the culprit can be reported upstream.
- `BeltInventory.tick` (HEAD): stacks grown in place after insertion are split the same way on the next tick.
- `TransportedItemStack.serializeNBT`: last resort, a copy clamped to 99 is written instead of throwing; the live stack is
  left for the tick guard. This path only logs, it never deletes items on its own.

Nothing is removed from the world; pieces keep travelling as normal belt items. Identical jar on client and server.

Build: `gradle build` (ModDevGradle 2.0, Java 21) with Create 6.0.10 and its bundled Flywheel/Ponder/Registrate jars
dropped into `libs/` (git-ignored). MIT.
