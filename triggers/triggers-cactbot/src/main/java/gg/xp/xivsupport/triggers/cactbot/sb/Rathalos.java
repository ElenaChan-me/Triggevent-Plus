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
import gg.xp.xivsupport.events.state.RawXivCombatantInfo;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcAbilityUsedCallout;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/trial/rathalos.ts をもとに移植
@CalloutRepo(name = "The Great Hunt", duty = KnownDuty.TheGreatHunt)
public class Rathalos extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long GARULA_NAME_ID = 6173;
	// ガルルガが出てから足場が使えるようになるまでの時間
	private static final long FOOTHOLD_DELAY_MS = 15_000;
	// 複数人に当たるアクションなので、まとめて1回にする
	private static final long MULTI_HIT_SUPPRESS_MS = 1_000;

	private XivState state;

	public Rathalos(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheGreatHunt);
	}

	// 前方扇範囲に加えて左側を尻尾で薙ぐので、右側面が一番わかりやすい安置になる
	@NpcCastCallout(0x286A)
	private final ModifiableCallout<AbilityCastStart> manglePhase1 = ModifiableCallout.durationBasedCall("Mangle (Phase 1)", "Right Flank or Out");

	@NpcCastCallout(0x287A)
	private final ModifiableCallout<AbilityCastStart> manglePhase2 = ModifiableCallout.durationBasedCall("Mangle (Phase 2)", "Away from Front");

	// 詠唱がなく、アクション自体が予兆を兼ねている
	@NpcAbilityUsedCallout(value = 0x286C, suppressMs = MULTI_HIT_SUPPRESS_MS)
	private final ModifiableCallout<AbilityUsedEvent> tailSwing = new ModifiableCallout<>("Tail Swing", "Left Flank or Out");

	@NpcCastCallout({0x2870, 0x2872})
	private final ModifiableCallout<AbilityCastStart> flamingRecoil = ModifiableCallout.durationBasedCall("Flaming Recoil", "Get Behind");

	@NpcCastCallout({0x286D, 0x2878})
	private final ModifiableCallout<AbilityCastStart> rush = ModifiableCallout.durationBasedCall("Rush", "Avoid Charge");

	private final ModifiableCallout<RawAddCombatantEvent> garulaAdd = new ModifiableCallout<>("Garula Add", "Go West");
	private final ModifiableCallout<RawAddCombatantEvent> garulaFoothold = new ModifiableCallout<>("Garula Foothold", "Use Foothold for QTE");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> garulaSq = SqtTemplates.sq(30_000,
			RawAddCombatantEvent.class, Rathalos::isGarula,
			(e1, s) -> {
				// 東から予兆を置いてくるので、西に逃げるのが一番わかりやすい
				s.updateCall(garulaAdd, e1);
				s.waitMs(FOOTHOLD_DELAY_MS);
				s.updateCall(garulaFoothold, e1);
			});

	private static boolean isGarula(RawAddCombatantEvent event) {
		RawXivCombatantInfo info = event.getFullInfo();
		long nameId = info != null ? info.getBnpcNameId() : event.getEntity().getbNpcNameId();
		return nameId == GARULA_NAME_ID;
	}

	@PlayerHeadmarker(0x0081)
	private final ModifiableCallout<HeadMarkerEvent> fireBreath = new ModifiableCallout<>("Fire Breath", "Spread");

	private final ModifiableCallout<HeadMarkerEvent> fireball = new ModifiableCallout<>("Fireball", "Stack on {event.target}");

	@HandleEvents
	public void fireball(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x005D)) {
			context.accept(fireball.getModified(event));
		}
	}

	@NpcAbilityUsedCallout(value = 0x2879, suppressMs = MULTI_HIT_SUPPRESS_MS)
	private final ModifiableCallout<AbilityUsedEvent> sweepingFlames = new ModifiableCallout<>("Sweeping Flames", "Away from Front");
}
