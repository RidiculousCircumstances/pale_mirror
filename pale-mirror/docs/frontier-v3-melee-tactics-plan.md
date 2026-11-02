# HOT melee tactics, accepted 2026-11-02

Implement four accepted improvements through the existing common combat module:
1. Native range hysteresis supplies short-lived exact HOLD/APPROACH facts. HOLD
   withdraws only an interruptible melee approach through the UAE checkpoint;
   family missions remain bookmarks and cannot award arrival.
2. A durable prepared strike is a cancellable windup, then one native strike,
   then recovery. Actual weapon/LOS/reach/incarnations are checked again. Use
   vanilla swing plus a bounded visible warning; no new damage/animation authority.
   Restart cannot revive an obsolete windup. The existing effect journal alone
   confirms actual damage/wear; cosmetic preparation is not a hit.
3. Generic expiring position reservations include unit dimensions. Existing role
   mission selection publishes the claim atomically; renewals and typed waits use
   registered combat events. Shared navigation/courtesy remains sole movement owner.
4. Keep ROUTE_PENDING, SPACE_OCCUPIED and confirmed NO_PATH/NO_POSITION distinct.
   Only confirmed lack of a position may trigger a bounded retry deferral. Guard
   policy resumes its retained post/search; common combat has no patrol knowledge.
   Target displacement/expiry of deferral or fresh native reach permits reconsideration.

No reinforcement-allocation changes, COLD damage, ranged combat or civilian combat.
Fresh schema320; no compatibility work. Rules belong to the hashed world ruleset.
Trace admission, movement interruption, lease renewal, native hit/cancel, target
loss/death and restart before focused negative/recovery checks. Extend existing
native carrier for windup/dodge and group claims; use one capped ordinary client.
Commit, detached verified build, normal MAIN fresh-world deployment. No push or agents.
