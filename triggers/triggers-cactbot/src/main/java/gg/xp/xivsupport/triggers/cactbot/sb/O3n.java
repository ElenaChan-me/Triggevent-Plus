package gg.xp.xivsupport.triggers.cactbot.sb;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.Job;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.models.XivCombatant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/raid/o3n.ts をもとに移植
@CalloutRepo(name = "Deltascape V30", duty = KnownDuty.DeltascapeV30)
public class O3n extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long HOLY_STACK_MARKER = 0x64;
	private static final long HOLY_PREY_MARKER = 0x65;
	// 頭割り1つと対象2つ
	private static final int HOLY_MARKER_COUNT = 3;
	// 1: 開幕、2: 洞窟フェーズ、3: 中間後
	private static final int CAVE_PHASE = 2;
	private static final int FROG_PHASE = 3;

	private XivState state;

	public O3n(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.DeltascapeV30);
	}

	private final List<XivCombatant> holyTargets = new ArrayList<>();
	private int holyCounter;
	private int gameCount;
	private int phaseNumber = 1;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		holyTargets.clear();
		holyCounter = 0;
		gameCount = 0;
		phaseNumber = 1;
	}

	@HandleEvents
	public void phaseTracker(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x2304)) {
			phaseNumber++;
			gameCount = 1;
		}
	}

	private boolean goodFrogTurn() {
		return phaseNumber == FROG_PHASE && gameCount % 2 == 0;
	}

	private final ModifiableCallout<HeadMarkerEvent> holyStackOnYou = new ModifiableCallout<>("Spellblade Holy: Stack (On You)", "Stack on You");
	private final ModifiableCallout<HeadMarkerEvent> holyOut = new ModifiableCallout<>("Spellblade Holy: Prey (On You)", "Out");
	private final ModifiableCallout<HeadMarkerEvent> holyStack = new ModifiableCallout<>("Spellblade Holy: Stack", "Stack on {stackTarget}");
	private final ModifiableCallout<HeadMarkerEvent> holyCaveSpread = new ModifiableCallout<>("Spellblade Holy (Cave)", "Spread");
	private final ModifiableCallout<HeadMarkerEvent> holyMindjackStack = new ModifiableCallout<>("Spellblade Holy (Mindjack)", "Stack on {event.target}");

	@HandleEvents
	public void spellbladeHoly(EventContext context, HeadMarkerEvent event) {
		if (!event.markerIdMatches(HOLY_STACK_MARKER, HOLY_PREY_MARKER)) {
			return;
		}
		// 洞窟フェーズには頭割りが無い。ログ上は頭割り、対象、対象の順に付く
		if (phaseNumber != CAVE_PHASE) {
			holyTargets.add(event.getTarget());
			if (holyTargets.size() == HOLY_MARKER_COUNT) {
				XivCombatant stackTarget = holyTargets.get(0);
				if (stackTarget.isThePlayer()) {
					context.accept(holyStackOnYou.getModified(event));
				}
				else if (holyTargets.stream().anyMatch(XivCombatant::isThePlayer)) {
					context.accept(holyOut.getModified(event));
				}
				else {
					context.accept(holyStack.getModified(event, Map.of("stackTarget", stackTarget)));
				}
				holyTargets.clear();
			}
		}
		if (phaseNumber == CAVE_PHASE && event.markerIdMatches(HOLY_PREY_MARKER) && event.getTarget().isThePlayer()) {
			context.accept(holyCaveSpread.getModified(event));
		}
		if (phaseNumber >= FROG_PHASE && event.markerIdMatches(HOLY_STACK_MARKER) && holyCounter % 2 == 0) {
			context.accept(holyMindjackStack.getModified(event));
			holyCounter++;
			holyTargets.clear();
		}
	}

	@NpcCastCallout(0x2471)
	private final ModifiableCallout<AbilityCastStart> crystalSquare = ModifiableCallout.durationBasedCall("The Queen's Waltz: Crystal Square", "Get on Crystal Square");

	private final ModifiableCallout<AbilityCastStart> goodRibbit = ModifiableCallout.durationBasedCall("Ribbit (Get Hit)", "Get Hit by Ribbit");
	private final ModifiableCallout<AbilityCastStart> badRibbit = ModifiableCallout.durationBasedCall("Ribbit", "Away from Front");

	@HandleEvents
	public void ribbit(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x2466)) {
			context.accept((goodFrogTurn() ? goodRibbit : badRibbit).getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> gameFrog = ModifiableCallout.durationBasedCall("The Game (Frog)", "Stand on Frog Tile");
	private final ModifiableCallout<AbilityCastStart> gameTank = ModifiableCallout.durationBasedCall("The Game (Tank)", "Stand on Shield");
	private final ModifiableCallout<AbilityCastStart> gameHealer = ModifiableCallout.durationBasedCall("The Game (Healer)", "Stand on Cross");
	private final ModifiableCallout<AbilityCastStart> gameDps = ModifiableCallout.durationBasedCall("The Game (DPS)", "Stand on Sword");

	@HandleEvents
	public void theGame(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x246D)) {
			return;
		}
		if (goodFrogTurn()) {
			context.accept(gameFrog.getModified(event));
		}
		else if (state.playerJobMatches(Job::isTank)) {
			context.accept(gameTank.getModified(event));
		}
		else if (state.playerJobMatches(Job::isHealer)) {
			context.accept(gameHealer.getModified(event));
		}
		else if (state.playerJobMatches(Job::isDps)) {
			context.accept(gameDps.getModified(event));
		}
		gameCount++;
	}

	@NpcCastCallout(0x2467)
	private final ModifiableCallout<AbilityCastStart> mindjackForward = ModifiableCallout.durationBasedCall("Mindjack (Forward)", "Mindjack: Forward");

	@NpcCastCallout(0x2468)
	private final ModifiableCallout<AbilityCastStart> mindjackBack = ModifiableCallout.durationBasedCall("Mindjack (Back)", "Mindjack: Back");

	@NpcCastCallout(0x2469)
	private final ModifiableCallout<AbilityCastStart> mindjackLeft = ModifiableCallout.durationBasedCall("Mindjack (Left)", "Mindjack: Left");

	@NpcCastCallout(0x246A)
	private final ModifiableCallout<AbilityCastStart> mindjackRight = ModifiableCallout.durationBasedCall("Mindjack (Right)", "Mindjack: Right");
}
