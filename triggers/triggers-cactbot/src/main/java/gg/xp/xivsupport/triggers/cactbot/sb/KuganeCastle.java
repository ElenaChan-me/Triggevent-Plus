package gg.xp.xivsupport.triggers.cactbot.sb;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcAbilityUsedCallout;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.models.XivCombatant;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/dungeon/kugane_castle.ts をもとに移植
@CalloutRepo(name = "Kugane Castle", duty = KnownDuty.KuganeCastle)
public class KuganeCastle extends AutoChildEventHandler implements FilteredEventHandler {

	// 大判小判は何度も出るので、一度知らせれば十分
	private static final long GRATUITY_SUPPRESS_MS = 15_000;
	private static final long DRAGONS_LAIR_SUPPRESS_MS = 5_000;

	private XivState state;

	public KuganeCastle(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.KuganeCastle);
	}

	@NpcCastCallout(0x1E93)
	private final ModifiableCallout<AbilityCastStart> kenkiRelease = ModifiableCallout.durationBasedCall("Kenki Release", "Raidwide");

	private final ModifiableCallout<HeadMarkerEvent> helmCrack = new ModifiableCallout<>("Helm Crack", "Stack on {event.target}");

	@HandleEvents
	public void helmCrack(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x3E)) {
			context.accept(helmCrack.getModified(event));
		}
	}

	// 線自体に技名はなく、約4秒後に付けられた相手へ自爆が来る
	private final ModifiableCallout<TetherEvent> cordage = new ModifiableCallout<>("Cordage", "Harakiri Tether on You");

	@HandleEvents
	public void cordage(EventContext context, TetherEvent event) {
		if (event.tetherIdMatches(0x11) && event.eitherTargetMatches(XivCombatant::isThePlayer)) {
			context.accept(cordage.getModified(event));
		}
	}

	@PlayerHeadmarker(0x5F)
	private final ModifiableCallout<HeadMarkerEvent> clockworkRaiton = new ModifiableCallout<>("Clockwork Raiton", "Spread");

	@NpcAbilityUsedCallout(value = 0x1EAE, suppressMs = GRATUITY_SUPPRESS_MS)
	private final ModifiableCallout<AbilityUsedEvent> gratuity = new ModifiableCallout<>("Gratuity", "Grab Gold Piles");

	@NpcAbilityUsedCallout(value = 0x1EA6, suppressMs = DRAGONS_LAIR_SUPPRESS_MS)
	private final ModifiableCallout<AbilityUsedEvent> dragonsLair = new ModifiableCallout<>("Dragon's Lair", "Kill Adds");

}
