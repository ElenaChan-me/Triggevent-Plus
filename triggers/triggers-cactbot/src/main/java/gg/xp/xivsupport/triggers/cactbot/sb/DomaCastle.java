package gg.xp.xivsupport.triggers.cactbot.sb;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.support.PlayerStatusCallout;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/dungeon/doma_castle.ts をもとに移植
@CalloutRepo(name = "Doma Castle", duty = KnownDuty.DomaCastle)
public class DomaCastle extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public DomaCastle(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.DomaCastle);
	}

	private boolean seenTowers;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		seenTowers = false;
	}

	// 魔導ヘキサローダー

	private final ModifiableCallout<HeadMarkerEvent> twoTonzeMissile = new ModifiableCallout<>("2-tonze Magitek Missile", "Stack on {event.target}");

	@HandleEvents
	public void twoTonzeMissile(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x3E)) {
			context.accept(twoTonzeMissile.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> magitekMissilesFirst = ModifiableCallout.durationBasedCall("Magitek Missiles (First)", "Get Tower");
	private final ModifiableCallout<AbilityCastStart> magitekMissiles = ModifiableCallout.durationBasedCall("Magitek Missiles", "Get Towers");

	@HandleEvents
	public void magitekMissiles(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x20A4)) {
			context.accept((seenTowers ? magitekMissiles : magitekMissilesFirst).getModified(event));
			seenTowers = true;
		}
	}

	// ハイパーチューンド・グリンワース

	@PlayerHeadmarker(0x63)
	private final ModifiableCallout<HeadMarkerEvent> delayActionCharge = new ModifiableCallout<>("Delay-action Charge", "Spread");

	// 接近型マーカーには頭上マーカーも対象ログもなく、ターゲティングのデバフしか出ない
	@PlayerStatusCallout(0x4E5)
	private final ModifiableCallout<BuffApplied> thermobaricCharge = new ModifiableCallout<BuffApplied>("Thermobaric Charge", "Drop Charge Away").statusIcon(0x4E5);

}
