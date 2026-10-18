package gg.xp.xivsupport.triggers.cactbot.shb;

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
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.support.PlayerStatusCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.ArenaPos;
import gg.xp.xivsupport.models.ArenaSector;
import gg.xp.xivsupport.models.XivCombatant;

import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/raid/e6s.ts をもとに移植
@CalloutRepo(name = "Edens Verse Furor Savage", duty = KnownDuty.EdensVerseFurorSavage)
public class E6s extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long HANDS_OF_FLAME = 0x4D00;
	// 蹴る分身の位置は中央から約8ずれるので、その中間で区切る
	private static final ArenaPos strikeSparkArena = new ArenaPos(100, 100, 5, 5);

	private XivState state;

	public E6s(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.EdensVerseFurorSavage);
	}

	private boolean bothPhase;
	private boolean handsOfFlame;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		bothPhase = false;
		handsOfFlame = false;
	}

	private final ModifiableCallout<AbilityCastStart> strikeSpark = new ModifiableCallout<>("Strike Spark", "{safe}");
	private final ModifiableCallout<AbilityCastStart> strikeSparkUnknown = new ModifiableCallout<>("Strike Spark (Unknown)", "Unknown");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> strikeSparkSq = SqtTemplates.sq(20_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x4BD3),
			(e1, s) -> {
				s.waitMs(10_800);
				s.refreshCombatants(200);
				// ボスと同名の分身のうち、一番新しい（IDが最大の）ものが安置を示す
				long nameId = e1.getSource().getbNpcNameId();
				Optional<XivCombatant> newest = state.getCombatantsListCopy().stream()
						.filter(cbt -> !cbt.isPc() && cbt.getbNpcNameId() == nameId && cbt.getPos() != null)
						.max(Comparator.comparingLong(XivCombatant::getId));
				ArenaSector safe = newest.map(strikeSparkArena::forCombatant).orElse(ArenaSector.UNKNOWN);
				if (safe == ArenaSector.UNKNOWN || safe == ArenaSector.CENTER) {
					s.updateCall(strikeSparkUnknown, e1);
				}
				else {
					s.setParam("safe", safe);
					s.updateCall(strikeSpark, e1);
				}
			});

	@NpcCastCallout(0x4BF7)
	private final ModifiableCallout<AbilityCastStart> superstorm = ModifiableCallout.durationBasedCall("Superstorm", "Raidwide");

	@NpcCastCallout({0x4BFE, 0x4BFF, 0x4C04, 0x4C05})
	private final ModifiableCallout<AbilityCastStart> ferostorm = ModifiableCallout.durationBasedCall("Ferostorm", "Avoid Green Nails");

	private final ModifiableCallout<HeadMarkerEvent> airBumpOnYou = new ModifiableCallout<>("Air Bump (On You)", "Enumeration on You");
	private final ModifiableCallout<HeadMarkerEvent> airBump = new ModifiableCallout<>("Air Bump", "Enumeration");
	private final RepeatSuppressor airBumpSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void headmarkers(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0xD3) && airBumpSupp.check(event)) {
			context.accept((event.getTarget().isThePlayer() ? airBumpOnYou : airBump).getModified(event));
		}
	}

	@NpcCastCallout(0x4C14)
	private final ModifiableCallout<AbilityCastStart> infernoHowl = ModifiableCallout.durationBasedCall("Inferno Howl", "Raidwide");

	@HandleEvents
	public void phases(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(HANDS_OF_FLAME)) {
			// 線は他のギミックでも使われるので、詠唱中かどうかを覚えておく
			handsOfFlame = true;
		}
		else if (event.abilityIdMatches(0x4F9F)) {
			bothPhase = true;
		}
		else if (event.abilityIdMatches(0x4BF7, 0x4C09)) {
			bothPhase = false;
		}
	}

	@HandleEvents
	public void abilities(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(HANDS_OF_FLAME)) {
			handsOfFlame = false;
		}
		else if (event.abilityIdMatches(0x4D55)) {
			bothPhase = false;
		}
	}

	private final ModifiableCallout<TetherEvent> handsOfFlameOnYou = new ModifiableCallout<>("Hands of Flame (On You)", "Charge on You");
	private final ModifiableCallout<TetherEvent> handsOfFlameSwap = new ModifiableCallout<>("Hands of Flame (Tank Swap)", "Tank Swap");

	private final ModifiableCallout<TetherEvent> ironsOfPurgatory = new ModifiableCallout<>("Irons of Purgatory", "Tethered to {player}");

	@HandleEvents
	public void tethers(EventContext context, TetherEvent event) {
		if (event.tetherIdMatches(0x68) && handsOfFlame) {
			if (event.getTarget().isThePlayer()) {
				context.accept(handsOfFlameOnYou.getModified(event));
			}
			else if (state.playerJobMatches(Job::isTank) && !bothPhase) {
				context.accept(handsOfFlameSwap.getModified(event));
			}
		}
		else if (event.tetherIdMatches(0x6C)) {
			XivCombatant partner;
			if (event.getSource().isThePlayer()) {
				partner = event.getTarget();
			}
			else if (event.getTarget().isThePlayer()) {
				partner = event.getSource();
			}
			else {
				return;
			}
			context.accept(ironsOfPurgatory.getModified(event, Map.of("player", partner)));
		}
	}

	@NpcCastCallout(0x4C0E)
	private final ModifiableCallout<AbilityCastStart> instantIncineration = ModifiableCallout.durationBasedCall("Instant Incineration", "Buster on {event.target}");

	@NpcCastCallout(0x4C0F)
	private final ModifiableCallout<AbilityCastStart> meteorStrike = ModifiableCallout.durationBasedCall("Meteor Strike", "Away from Front");

	@PlayerHeadmarker(0x16)
	private final ModifiableCallout<HeadMarkerEvent> handsOfHell = new ModifiableCallout<>("Hands of Hell", "Tether Marker on You");

	@PlayerStatusCallout(0x8BB)
	private final ModifiableCallout<BuffApplied> hatedOfTheVortex = new ModifiableCallout<BuffApplied>("Hated of the Vortex", "Attack Garuda").statusIcon(0x8BB);

	@PlayerStatusCallout(0x8BC)
	private final ModifiableCallout<BuffApplied> hatedOfTheEmbers = new ModifiableCallout<BuffApplied>("Hated of the Embers", "Attack Ifrit").statusIcon(0x8BC);

	@NpcCastCallout({0x4BFB, 0x4BFC})
	private final ModifiableCallout<AbilityCastStart> downburst = ModifiableCallout.durationBasedCall("Downburst", "Knockback");

	private final ModifiableCallout<AbilityCastStart> conflagStrike = ModifiableCallout.durationBasedCall("Conflag Strike", "Go to Chain Spots");
	private final ModifiableCallout<AbilityCastStart> conflagStrikeBehind = new ModifiableCallout<>("Conflag Strike (Follow-up)", "Get Behind");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> conflagStrikeSq = SqtTemplates.sq(40_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x4C10),
			(e1, s) -> {
				s.updateCall(conflagStrike, e1);
				s.waitMs(31_000);
				s.updateCall(conflagStrikeBehind, e1);
			});

}
