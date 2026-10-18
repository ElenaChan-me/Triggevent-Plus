package gg.xp.xivsupport.triggers.cactbot.ew;

import gg.xp.reevent.events.BaseEvent;
import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.AutoFeed;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.Job;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.actlines.events.MapEffectEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcAbilityUsedCallout;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;

// cactbot（Apache License 2.0）の ui/raidboss/data/06-ew/trial/rubicante.ts をもとに移植
@CalloutRepo(name = "Mount Ordeals", duty = KnownDuty.MountOrdeals)
public class Rubicante extends AutoChildEventHandler implements FilteredEventHandler {

	// 0x05 から順に北、北東…北西。煉獄の炎の始点になる
	private static final long PURGATION_FIRST_LOCATION = 0x05;
	private static final String[] PURGATION_DIRECTIONS = {"North", "Northeast", "East", "Southeast", "South", "Southwest", "West", "Northwest"};
	private static final long PURGATION_STRAIGHT_LINE = 0x00010004;
	private static final long PURGATION_CW_LINE = 0x00020004;
	private static final long PURGATION_CCW_LINE = 0x00080004;
	private static final long PURGATION_MID_RING_LOCATION = 0x02;
	private static final long PURGATION_MID_ROTATE_CW = 0x00020001;
	private static final long PURGATION_MID_ROTATE_CCW = 0x00200010;

	private XivState state;

	public Rubicante(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.MountOrdeals);
	}

	private int purgation = 1;
	// -1 は未確定
	private int purgationIdx = -1;
	private long purgationLine;
	private long purgationMidRotate;
	private boolean dualfireTarget;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		purgation = 1;
		clearPurgation();
		dualfireTarget = false;
	}

	private void clearPurgation() {
		purgationIdx = -1;
		purgationLine = 0;
		purgationMidRotate = 0;
	}

	@NpcCastCallout(0x7CEA)
	private final ModifiableCallout<AbilityCastStart> inferno = ModifiableCallout.durationBasedCall("Inferno (Raidwide)", "Raidwide");

	@HandleEvents
	public void purgationMapEffects(EventContext context, MapEffectEvent event) {
		long idx = event.getLocation() - PURGATION_FIRST_LOCATION;
		if (idx >= 0 && idx < PURGATION_DIRECTIONS.length
				&& event.flagsMatches(PURGATION_STRAIGHT_LINE, PURGATION_CW_LINE, PURGATION_CCW_LINE)) {
			purgationIdx = (int) idx;
			purgationLine = event.getFlags();
		}
		else if (event.getLocation() == PURGATION_MID_RING_LOCATION
				&& event.flagsMatches(PURGATION_MID_ROTATE_CW, PURGATION_MID_ROTATE_CCW)) {
			purgationMidRotate = event.getFlags();
		}
	}

	private final ModifiableCallout<AbilityCastStart> ordealOfPurgation = new ModifiableCallout<>("Ordeal of Purgation", "Avoid Cone from {dir}", 8_000);
	private final ModifiableCallout<AbilityCastStart> ordealOfPurgationUnknown = new ModifiableCallout<>("Ordeal of Purgation (Unknown)", "Avoid Cone", 8_000);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> ordealOfPurgationSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x7CC4, 0x80E8),
			(e1, s) -> {
				// マップエフェクトが詠唱と同時に来るので少し待つ
				s.waitMs(200);
				int idx = purgationIdx;
				if (idx >= 0) {
					int mod = 0;
					if (purgation == 2) {
						// 2回目は中央の線が曲がるだけで回転しない
						if (purgationLine == PURGATION_CW_LINE) {
							mod = 1;
						}
						else if (purgationLine == PURGATION_CCW_LINE) {
							mod = -1;
						}
						else {
							idx = -1;
						}
					}
					else if (purgation > 2) {
						// 3回目以降は直線で、中央の回転と逆向きに曲がる
						if (purgationMidRotate == PURGATION_MID_ROTATE_CCW) {
							mod = 1;
						}
						else if (purgationMidRotate == PURGATION_MID_ROTATE_CW) {
							mod = -1;
						}
						else {
							idx = -1;
						}
					}
					if (idx >= 0) {
						idx = (idx + PURGATION_DIRECTIONS.length + mod) % PURGATION_DIRECTIONS.length;
					}
				}
				purgation++;
				clearPurgation();
				if (idx < 0) {
					s.updateCall(ordealOfPurgationUnknown, e1);
					return;
				}
				s.setParam("dir", PURGATION_DIRECTIONS[idx]);
				s.updateCall(ordealOfPurgation, e1);
			});

	@NpcCastCallout(0x7CEB)
	private final ModifiableCallout<AbilityCastStart> shatteringHeat = ModifiableCallout.durationBasedCall("Shattering Heat", "Buster on {event.target}");

	private final ModifiableCallout<AbilityCastStart> blazingRapture = ModifiableCallout.durationBasedCall("Blazing Rapture", "Big Raidwide");

	// 14秒ほどの長い詠唱なので、始まってすぐには呼ばない
	@AutoFeed
	private final SequentialTrigger<BaseEvent> blazingRaptureSq = SqtTemplates.sq(20_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x7CD2),
			(e1, s) -> {
				s.waitMs(5_000);
				s.updateCall(blazingRapture, e1);
			});

	@NpcCastCallout(value = 0x7CDA, onYou = true)
	private final ModifiableCallout<AbilityCastStart> infernoSpread = ModifiableCallout.durationBasedCall("Inferno (Spread)", "Spread");

	private final ModifiableCallout<AbilityCastStart> flamerake = ModifiableCallout.durationBasedCall("Flamerake", "Avoid Line Cleave, then In");

	// 方位盤の回転演出が6秒あり、その後2.6秒で着弾する
	@AutoFeed
	private final SequentialTrigger<BaseEvent> flamerakeSq = SqtTemplates.sq(15_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x7CDD),
			(e1, s) -> {
				s.waitMs(3_000);
				s.updateCall(flamerake, e1);
			});

	@NpcAbilityUsedCallout(value = 0x7CE8, suppressMs = 500)
	private final ModifiableCallout<AbilityUsedEvent> soulscald = new ModifiableCallout<>("Soulscald", "Move Away");

	private final ModifiableCallout<HeadMarkerEvent> dualfireOnYou = new ModifiableCallout<>("Dualfire (on You)", "Tank Cleave on You");

	// 自己対象の詠唱 0x7D95 の直前に付く
	@HandleEvents
	public void dualfireMarker(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0xE6) && event.getTarget().isThePlayer()) {
			dualfireTarget = true;
			context.accept(dualfireOnYou.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> dualfireHealer = ModifiableCallout.durationBasedCall("Dualfire (Healer)", "Tank Cleaves");
	private final ModifiableCallout<AbilityCastStart> dualfireAvoid = ModifiableCallout.durationBasedCall("Dualfire (Avoid)", "Avoid Tank Cleaves");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> dualfireSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x7D95),
			(e1, s) -> {
				s.waitMs(500);
				if (!dualfireTarget) {
					s.updateCall(state.playerJobMatches(Job::isHealer) ? dualfireHealer : dualfireAvoid, e1);
				}
				dualfireTarget = false;
			});

	@NpcCastCallout(0x7CE3)
	private final ModifiableCallout<AbilityCastStart> sweepingImmolation = ModifiableCallout.durationBasedCall("Sweeping Immolation", "Stack Behind Boss");

}
