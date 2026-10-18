package gg.xp.xivsupport.triggers.cactbot.arr;

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
import gg.xp.xivsupport.events.state.combatstate.StatusEffectRepository;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.XivCombatant;

import java.time.Duration;
import java.util.List;
import java.util.Map;

// cactbot（Apache License 2.0）の ui/raidboss/data/02-arr/raid/t9.ts をもとに移植
@CalloutRepo(name = "The Second Coil Of Bahamut Turn4", duty = KnownDuty.TheSecondCoilOfBahamutTurn4)
public class T9 extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long RAVEN_BLIGHT = 0x1CA;
	private static final long GAROTTE_TWIST = 0x1CE;
	private static final Duration RAVEN_BLIGHT_CALL_AT = Duration.ofSeconds(5);
	private static final List<String> DIVE_MARKERS = List.of("A", "B", "C");

	private XivState state;
	private StatusEffectRepository buffs;

	public T9(XivState state, StatusEffectRepository buffs) {
		this.state = state;
		this.buffs = buffs;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheSecondCoilOfBahamutTurn4);
	}

	private int tetherCount;
	private int diveMarkerCount;

	@HandleEvents
	public void reset(PullStartedEvent unused) {
		tetherCount = 0;
		diveMarkerCount = 0;
	}

	private final ModifiableCallout<BuffApplied> ravenBlightOnYou = ModifiableCallout.<BuffApplied>durationBasedCall("Raven Blight on You", "Blight on You")
			.statusIcon(RAVEN_BLIGHT);
	private final ModifiableCallout<BuffApplied> ravenBlightOnOther = ModifiableCallout.<BuffApplied>durationBasedCall("Raven Blight on Other", "Blight on {event.target}")
			.statusIcon(RAVEN_BLIGHT);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> ravenBlightOnYouSq = SqtTemplates.callWhenDurationIs(BuffApplied.class,
			ba -> ba.buffIdMatches(RAVEN_BLIGHT) && ba.getTarget().isThePlayer(),
			ravenBlightOnYou, RAVEN_BLIGHT_CALL_AT);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> ravenBlightOnOtherSq = SqtTemplates.callWhenDurationIs(BuffApplied.class,
			ba -> ba.buffIdMatches(RAVEN_BLIGHT) && !ba.getTarget().isThePlayer(),
			ravenBlightOnOther, RAVEN_BLIGHT_CALL_AT);

	@PlayerHeadmarker({0x07, 0x0A, 0x09})
	private final ModifiableCallout<HeadMarkerEvent> meteor = new ModifiableCallout<>("Meteor", "Meteor on You");

	@PlayerHeadmarker(0x08)
	private final ModifiableCallout<HeadMarkerEvent> meteorStream = new ModifiableCallout<>("Meteor Stream", "Spread");

	private final ModifiableCallout<HeadMarkerEvent> thermoOnYou = new ModifiableCallout<>("Thermionic Beam on You", "Thermo on You");
	private final ModifiableCallout<HeadMarkerEvent> thermoOnOther = new ModifiableCallout<>("Thermionic Beam on Other", "Stack on {event.target}");
	private final ModifiableCallout<HeadMarkerEvent> diveMarker = new ModifiableCallout<>("Dragon Dive Marker", "Go to {marker}");

	@HandleEvents
	public void headmarkers(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x0F)) {
			context.accept((event.getTarget().isThePlayer() ? thermoOnYou : thermoOnOther).getModified(event));
		}
		else if (event.markerIdMatches(0x14)) {
			// マーカーは3人に順番に付くので、何番目かで向かう場所が決まる
			int index = diveMarkerCount++;
			if (event.getTarget().isThePlayer() && index < DIVE_MARKERS.size()) {
				context.accept(diveMarker.getModified(event, Map.<String, Object>of("marker", DIVE_MARKERS.get(index))));
			}
		}
	}

	private final ModifiableCallout<AbilityCastStart> earthshock = ModifiableCallout.durationBasedCall("Earthshock", "Silence Blue Golem");

	@NpcCastCallout(0x83B)
	private final ModifiableCallout<AbilityCastStart> heavensfall = ModifiableCallout.durationBasedCall("Heavensfall", "Heavensfall");

	@HandleEvents
	public void casts(EventContext context, AbilityCastStart event) {
		if (event.getSource().isPc()) {
			return;
		}
		if (event.abilityIdMatches(0x7F5) && state.playerJobMatches(Job::caresAboutInterrupt)) {
			context.accept(earthshock.getModified(event));
		}
		else if (event.abilityIdMatches(0x7E6)) {
			// 最終フェーズ開始でテザーとダイブマーカーの数え直し
			tetherCount = 0;
			diveMarkerCount = 0;
		}
	}

	private final ModifiableCallout<BuffApplied> garotteOnYou = new ModifiableCallout<BuffApplied>("Garotte Twist on You", "Garotte on You")
			.statusIcon(GAROTTE_TWIST);

	@HandleEvents
	public void garotteGain(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(GAROTTE_TWIST) && event.getTarget().isThePlayer() && !event.isRefresh()) {
			context.accept(garotteOnYou.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> cleanseGarotte = new ModifiableCallout<>("Ghost Death (Garotte)", "Cleanse Garotte");
	private final ModifiableCallout<AbilityUsedEvent> thunderOnYou = new ModifiableCallout<>("Thunder on You", "Thunder on You");

	// 亡霊の自爆は複数人に当たるので一度だけ呼ぶ
	private final RepeatSuppressor ghostDeathSupp = new RepeatSuppressor(Duration.ofSeconds(3));

	@HandleEvents
	public void abilities(EventContext context, AbilityUsedEvent event) {
		if (event.getSource().isPc()) {
			return;
		}
		if (event.abilityIdMatches(0x7FA)) {
			if (buffs.isStatusOnPlayer(GAROTTE_TWIST) && ghostDeathSupp.check(event)) {
				context.accept(cleanseGarotte.getModified(event));
			}
		}
		else if (event.abilityIdMatches(0x7FD) && event.getTarget().isThePlayer()) {
			context.accept(thunderOnYou.getModified(event));
		}
	}

	private final ModifiableCallout<TetherEvent> fireOutOnYou = new ModifiableCallout<>("Firehorn Tether: Out (on You)", "Fire Out on You");
	private final ModifiableCallout<TetherEvent> fireInOnYou = new ModifiableCallout<>("Firehorn Tether: In (on You)", "Fire In on You");
	private final ModifiableCallout<TetherEvent> fireOutOnOther = new ModifiableCallout<>("Firehorn Tether: Out (on Other)", "Fire Out on {player}");
	private final ModifiableCallout<TetherEvent> fireInOnOther = new ModifiableCallout<>("Firehorn Tether: In (on Other)", "Fire In on {player}");

	@HandleEvents
	public void fireTether(EventContext context, TetherEvent event) {
		if (!event.tetherIdMatches(0x05)) {
			return;
		}
		tetherCount++;
		XivCombatant player = event.getTargetMatching(XivCombatant::isPc);
		if (player == null) {
			return;
		}
		// 外、中、外、中の順に交互に来る
		boolean out = tetherCount % 2 == 1;
		ModifiableCallout<TetherEvent> call;
		if (player.isThePlayer()) {
			call = out ? fireOutOnYou : fireInOnYou;
		}
		else {
			call = out ? fireOutOnOther : fireInOnOther;
		}
		context.accept(call.getModified(event, Map.<String, Object>of("player", player)));
	}

}
