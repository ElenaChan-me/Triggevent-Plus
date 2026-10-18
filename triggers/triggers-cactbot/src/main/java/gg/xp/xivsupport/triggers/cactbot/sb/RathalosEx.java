package gg.xp.xivsupport.triggers.cactbot.sb;

import gg.xp.reevent.events.BaseEvent;
import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.AutoFeed;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.actlines.events.RawAddCombatantEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcAbilityUsedCallout;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/trial/rathalos-ex.ts をもとに移植
@CalloutRepo(name = "The Great Hunt Extreme", duty = KnownDuty.TheGreatHuntExtreme)
public class RathalosEx extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long GARULA = 6173;
	// 複数人に当たるので、最初の1回だけ拾う
	private static final long MULTI_HIT_SUPPRESS_MS = 1_000;
	// ガルラが出てから足場を使えるようになるまでの時間
	private static final long GARULA_QTE_DELAY_MS = 15_000;
	private static final int GARULA_TIMEOUT_MS = 20_000;

	private XivState state;

	public RathalosEx(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheGreatHuntExtreme);
	}

	// 前方扇と左側の尻尾なので、右側面が完全に安全
	@NpcCastCallout(0x2853)
	private final ModifiableCallout<AbilityCastStart> manglePhase1 = ModifiableCallout.durationBasedCall("Mangle (Phase 1)", "Right Flank or Out");

	@NpcCastCallout(0x2863)
	private final ModifiableCallout<AbilityCastStart> manglePhase2 = ModifiableCallout.durationBasedCall("Mangle (Phase 2)", "Away from Front");

	@NpcAbilityUsedCallout(value = 0x2855, suppressMs = MULTI_HIT_SUPPRESS_MS)
	private final ModifiableCallout<AbilityUsedEvent> tailSwing = new ModifiableCallout<>("Tail Swing", "Left Flank or Out");

	// 一撃で倒れることがあり、前方180度なので後ろへ
	@NpcCastCallout({0x2859, 0x285B})
	private final ModifiableCallout<AbilityCastStart> flamingRecoil = ModifiableCallout.durationBasedCall("Flaming Recoil", "Get Behind");

	@NpcCastCallout({0x2856, 0x2861})
	private final ModifiableCallout<AbilityCastStart> rush = ModifiableCallout.durationBasedCall("Rush", "Avoid Charge");

	private final ModifiableCallout<RawAddCombatantEvent> garulaAdd = new ModifiableCallout<>("Garula Add", "Go West");
	private final ModifiableCallout<RawAddCombatantEvent> garulaQte = new ModifiableCallout<>("Garula Targetable", "Use Foothold for QTE");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> garulaSq = SqtTemplates.sq(GARULA_TIMEOUT_MS,
			RawAddCombatantEvent.class, ace -> ace.getEntity().getbNpcNameId() == GARULA,
			(e1, s) -> {
				// 東から突進してくるので、西へ逃げるよう伝える
				s.updateCall(garulaAdd, e1);
				s.waitMs(GARULA_QTE_DELAY_MS);
				s.updateCall(garulaQte, e1);
			});

	@PlayerHeadmarker(0x81)
	private final ModifiableCallout<HeadMarkerEvent> fireBreath = new ModifiableCallout<>("Fire Breath", "Spread");

	private final ModifiableCallout<HeadMarkerEvent> fireballInitial = new ModifiableCallout<>("Fireball (First)", "Stack on {event.target}");
	private final ModifiableCallout<HeadMarkerEvent> fireball = new ModifiableCallout<>("Fireball (Second/Third)", "Stack on {event.target}");

	@HandleEvents
	public void fireball(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x5D)) {
			context.accept(fireballInitial.getModified(event));
		}
		else if (event.markerIdMatches(0x84)) {
			context.accept(fireball.getModified(event));
		}
	}

	@NpcAbilityUsedCallout(value = 0x2862, suppressMs = MULTI_HIT_SUPPRESS_MS)
	private final ModifiableCallout<AbilityUsedEvent> sweepingFlames = new ModifiableCallout<>("Sweeping Flames", "Away from Front");

}
