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
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.actlines.events.RawAddCombatantEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.RawXivCombatantInfo;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;

import java.time.Duration;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/dungeon/castrum_abania.ts をもとに移植
@CalloutRepo(name = "Castrum Abania", duty = KnownDuty.CastrumAbania)
public class CastrumAbania extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long INFERNO_ADD_NAME_ID = 6270;

	private XivState state;

	public CastrumAbania(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.CastrumAbania);
	}

	private boolean calledWildSpeed;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		calledWildSpeed = false;
	}

	// マグナ・ローダー

	private final ModifiableCallout<AbilityCastStart> fireIII = ModifiableCallout.durationBasedCall("Fire III", "Raidwide");

	@HandleEvents
	public void fireIII(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x1F16)) {
			calledWildSpeed = false;
			context.accept(fireIII.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> wildSpeed = new ModifiableCallout<>("Wild Speed", "Kill Adds");

	// ワイルドスピードは延々と繰り返すので、フェーズごとに最初の1回だけ知らせる
	@AutoFeed
	private final SequentialTrigger<BaseEvent> wildSpeedSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x207E) && !calledWildSpeed,
			(e1, s) -> {
				calledWildSpeed = true;
				s.waitMs(6_000);
				s.updateCall(wildSpeed, e1);
			});

	// 実験体XXIV号

	@NpcCastCallout({0x82A8, 0x82A9, 0x82AA, 0x82AB, 0x82AC, 0x82AD})
	private final ModifiableCallout<AbilityCastStart> elementalOverload = ModifiableCallout.durationBasedCall("Elemental Overload", "Raidwide");

	private final ModifiableCallout<HeadMarkerEvent> fireII = new ModifiableCallout<>("Fire II", "Stack on {event.target}");

	@HandleEvents
	public void fireII(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0xA1)) {
			context.accept(fireII.getModified(event));
		}
	}

	@PlayerHeadmarker(0x178)
	private final ModifiableCallout<HeadMarkerEvent> blizzardII = new ModifiableCallout<>("Blizzard II", "Spread");

	@NpcCastCallout(value = 0x82B8, suppressMs = 1_000)
	private final ModifiableCallout<AbilityCastStart> thunderII = ModifiableCallout.durationBasedCall("Thunder II", "Get Towers");

	// インフェルノ

	@NpcCastCallout({0x1F26, 0x208B, 0x208C})
	private final ModifiableCallout<AbilityCastStart> ketuSlash = ModifiableCallout.durationBasedCall("Ketu Slash", "Buster on {event.target}");

	private final ModifiableCallout<RawAddCombatantEvent> infernoAdds = new ModifiableCallout<>("Inferno Adds", "Kill Adds");
	private final RepeatSuppressor infernoAddsSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@HandleEvents
	public void addedCombatant(EventContext context, RawAddCombatantEvent event) {
		RawXivCombatantInfo info = event.getFullInfo();
		long nameId = info != null ? info.getBnpcNameId() : event.getEntity().getbNpcNameId();
		if (nameId == INFERNO_ADD_NAME_ID && infernoAddsSupp.check(event)) {
			context.accept(infernoAdds.getModified(event));
		}
	}

	@PlayerHeadmarker(0x4A)
	private final ModifiableCallout<HeadMarkerEvent> rahuRay = new ModifiableCallout<>("Rahu Ray", "Spread");

	// 強化前のラーフ・コメットはノックバックしないので、強化版の詠唱だけを拾う
	@NpcCastCallout({0x2088, 0x2089})
	private final ModifiableCallout<AbilityCastStart> rahuComet = ModifiableCallout.durationBasedCall("Rahu Comet", "Small Comet Knockback");

}
