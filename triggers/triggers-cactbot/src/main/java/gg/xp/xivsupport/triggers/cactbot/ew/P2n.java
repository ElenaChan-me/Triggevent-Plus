package gg.xp.xivsupport.triggers.cactbot.ew;

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
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.models.ArenaSector;
import gg.xp.xivsupport.models.Position;
import gg.xp.xivsupport.models.XivCombatant;

import java.util.List;
import java.util.Map;

// cactbot（Apache License 2.0）の ui/raidboss/data/06-ew/raid/p2n.ts をもとに移植
@CalloutRepo(name = "Asphodelos The Second Circle", duty = KnownDuty.AsphodelosTheSecondCircle)
public class P2n extends AutoChildEventHandler implements FilteredEventHandler {

	// HPを持っている本体のヒッポカムポス
	private static final long HIPPO_NPC_ID = 13721;
	private static final double ARENA_CENTER_X = 100;

	private XivState state;

	public P2n(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.AsphodelosTheSecondCircle);
	}

	private boolean flareOnMe;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		flareOnMe = false;
	}

	@NpcCastCallout(0x680F)
	private final ModifiableCallout<AbilityCastStart> murkyDepths = ModifiableCallout.durationBasedCall("Murky Depths", "Raidwide");

	@NpcCastCallout(0x680E)
	private final ModifiableCallout<AbilityCastStart> doubledImpact = ModifiableCallout.durationBasedCall("Doubled Impact", "Shared Buster on {event.target}");

	private final ModifiableCallout<AbilityCastStart> spokenCataractCorners = ModifiableCallout.durationBasedCall("Spoken Cataract (Corners)", "{safe} Corners");
	private final ModifiableCallout<AbilityCastStart> spokenCataractSide = ModifiableCallout.durationBasedCall("Spoken Cataract (Side)", "{safe}");

	// 本体の向きで安置が決まるので、向きが反映されるのを待ってから取得する
	@AutoFeed
	private final SequentialTrigger<BaseEvent> spokenCataractSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x67F8, 0x67F7, 0x67F9),
			(e1, s) -> {
				s.waitThenRefreshCombatants(1_000);
				List<XivCombatant> hippos = state.npcsById(HIPPO_NPC_ID);
				if (hippos.size() != 1) {
					return;
				}
				Position pos = hippos.get(0).getPos();
				if (pos == null) {
					return;
				}
				// 北=0, 東=1, 南=2, 西=3 の4方向に丸める
				int heading = Math.floorMod(Math.round(2 - 2 * pos.getHeading() / Math.PI), 4);
				List<ArenaSector> cardinals = ArenaSector.cardinals;
				if (e1.abilityIdMatches(0x67F8)) {
					s.updateCall(spokenCataractCorners.getModified(e1, Map.of("safe", cardinals.get(heading))));
				}
				else if (e1.abilityIdMatches(0x67F7)) {
					s.updateCall(spokenCataractSide.getModified(e1, Map.of("safe", cardinals.get((heading + 3) % 4))));
				}
				else {
					s.updateCall(spokenCataractSide.getModified(e1, Map.of("safe", cardinals.get((heading + 1) % 4))));
				}
			});

	@NpcCastCallout(0x67F6)
	private final ModifiableCallout<AbilityCastStart> sewageDeluge = ModifiableCallout.durationBasedCall("Sewage Deluge", "Raidwide");

	// 一部のプレイヤーにだけ付く散開
	private final ModifiableCallout<AbilityCastStart> taintedFlood = ModifiableCallout.durationBasedCall("Tainted Flood", "Spread");

	@HandleEvents
	public void taintedFloodCast(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x6809) && event.getTarget().isThePlayer()) {
			context.accept(taintedFlood.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> predatorySight = ModifiableCallout.durationBasedCall("Predatory Sight", "Dorito Stack");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> predatorySightSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x680A),
			(e1, s) -> {
				s.waitMs(3_000);
				s.updateCall(predatorySight, e1);
			});

	private final ModifiableCallout<HeadMarkerEvent> coherenceFlare = new ModifiableCallout<>("Coherence (Flare)", "Flare on You");
	private final ModifiableCallout<AbilityUsedEvent> coherenceStack = new ModifiableCallout<>("Coherence (Line Stack)", "Line Stack on {event.target}");

	// フレアのマーカーは必ず直線頭割りの対象決定より先に来る
	@HandleEvents
	public void coherenceFlareMarker(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x57) && event.getTarget().isThePlayer()) {
			flareOnMe = true;
			context.accept(coherenceFlare.getModified(event));
		}
	}

	// 詠唱 6801 の直前に、対象者付きの 6D14 が出る
	@HandleEvents
	public void coherenceStackTarget(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x6D14)) {
			if (!flareOnMe) {
				context.accept(coherenceStack.getModified(event));
			}
			flareOnMe = false;
		}
	}

	@NpcCastCallout(0x6807)
	private final ModifiableCallout<AbilityCastStart> shockwave = ModifiableCallout.durationBasedCall("Shockwave", "Knockback");

	private final ModifiableCallout<AbilityCastStart> dissociation = ModifiableCallout.durationBasedCall("Dissociation", "{safe}");

	// 場外の頭がいる側の反対が安全
	@HandleEvents
	public void dissociationCast(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x6806)) {
			return;
		}
		Position pos = state.getLatestCombatantData(event.getSource()).getPos();
		if (pos == null) {
			return;
		}
		if (pos.getX() > ARENA_CENTER_X) {
			context.accept(dissociation.getModified(event, Map.of("safe", ArenaSector.WEST)));
		}
		else if (pos.getX() < ARENA_CENTER_X) {
			context.accept(dissociation.getModified(event, Map.of("safe", ArenaSector.EAST)));
		}
	}

}
