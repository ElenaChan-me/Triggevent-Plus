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
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.actlines.events.RawAddCombatantEvent;
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcAbilityUsedCallout;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.XivCombatant;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/dungeon/swallows_compass.ts をもとに移植
@CalloutRepo(name = "The Swallows Compass", duty = KnownDuty.TheSwallowsCompass)
public class SwallowsCompass extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long TENGU_EMBER = 7201;
	private static final long LONG_END_TETHER = 0x29;

	private XivState state;

	public SwallowsCompass(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheSwallowsCompass);
	}

	private boolean dynamo;
	private boolean seenIntermission;
	private final List<XivCombatant> longEndTethered = new ArrayList<>();

	@HandleEvents
	public void reset(PullStartedEvent unused) {
		dynamo = false;
		seenIntermission = false;
		longEndTethered.clear();
	}

	// 大天狗

	@NpcCastCallout(0x2B95)
	private final ModifiableCallout<AbilityCastStart> tenguClout = ModifiableCallout.durationBasedCall("Tengu Clout", "Raidwide");

	@NpcCastCallout(0x2B94)
	private final ModifiableCallout<AbilityCastStart> tenguMight = ModifiableCallout.durationBasedCall("Tengu Might", "Buster on {event.target}");

	@NpcCastCallout(0x2B97)
	private final ModifiableCallout<AbilityCastStart> tenguWile = ModifiableCallout.durationBasedCall("Tengu Wile", "Look Away");

	private final ModifiableCallout<RawAddCombatantEvent> emberSpawn = new ModifiableCallout<>("Tengu Ember Spawn", "Avoid Fire Orbs");
	private final RepeatSuppressor emberSpawnSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@HandleEvents
	public void emberSpawn(EventContext context, RawAddCombatantEvent event) {
		if (event.getEntity().getbNpcNameId() == TENGU_EMBER && emberSpawnSupp.check(event)) {
			context.accept(emberSpawn.getModified(event));
		}
	}

	@NpcCastCallout(value = 0x2898, suppressMs = 5_000)
	private final ModifiableCallout<AbilityCastStart> flamesOfHate = ModifiableCallout.durationBasedCall("Flames Of Hate", "Away From Fireballs");

	// ダイダラボッチ

	@NpcCastCallout(0x2B9D)
	private final ModifiableCallout<AbilityCastStart> rightPalm = ModifiableCallout.durationBasedCall("Right Palm", "Left");

	@NpcCastCallout(0x2B9E)
	private final ModifiableCallout<AbilityCastStart> leftPalm = ModifiableCallout.durationBasedCall("Left Palm", "Right");

	@PlayerHeadmarker(0x87)
	private final ModifiableCallout<HeadMarkerEvent> mountainFalls = new ModifiableCallout<>("Mountain Falls", "Spread");

	@PlayerHeadmarker(0x1)
	private final ModifiableCallout<HeadMarkerEvent> mirage = new ModifiableCallout<>("Mirage", "5x Puddles on You");

	@NpcAbilityUsedCallout(value = 0x2BA3, suppressMs = 1_000)
	private final ModifiableCallout<AbilityUsedEvent> mythmaker = new ModifiableCallout<>("Mythmaker", "Raidwide");

	private final ModifiableCallout<BuffApplied> sixFulmsUnder = new ModifiableCallout<>("Six Fulms Under", "Out of the Lake");
	private final RepeatSuppressor sixFulmsUnderSupp = new RepeatSuppressor(Duration.ofSeconds(2));

	@HandleEvents
	public void sixFulmsUnder(EventContext context, BuffApplied event) {
		// 湖の中に居続けると何度も付与されるので、その都度呼び直す
		if (event.buffIdMatches(0x237) && event.getTarget().isThePlayer() && sixFulmsUnderSupp.check(event)) {
			context.accept(sixFulmsUnder.getModified(event));
		}
	}

	// 斉天大聖

	@NpcCastCallout(value = {0x2BA6, 0x2D07}, suppressMs = 5_000)
	private final ModifiableCallout<AbilityCastStart> shortEnd = ModifiableCallout.durationBasedCall("Short End", "Buster on {event.target}");

	@NpcCastCallout(value = {0x2BAA, 0x2D08}, suppressMs = 5_000)
	private final ModifiableCallout<AbilityCastStart> mountHuaguo = ModifiableCallout.durationBasedCall("Mount Huaguo", "Raidwide");

	@HandleEvents
	public void bothEndsCollect(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x2BA9, 0x2BAF)) {
			dynamo = true;
		}
	}

	private final ModifiableCallout<AbilityCastStart> bothEndsDynamo = ModifiableCallout.durationBasedCall("Both Ends (Blue)", "Close to Blue Staff");
	private final ModifiableCallout<AbilityCastStart> bothEndsChariot = ModifiableCallout.durationBasedCall("Both Ends (Red)", "Away from Red Staff");
	private final RepeatSuppressor bothEndsSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	// 青と赤が同時に来ることがあり、青があれば必ず内側が安置になるので、揃うのを待ってから判定する
	@AutoFeed
	private final SequentialTrigger<BaseEvent> bothEndsSq = SqtTemplates.sq(5_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x2BA8, 0x2BA9, 0x2BAE, 0x2BAF) && bothEndsSupp.check(acs),
			(e1, s) -> {
				s.waitMs(500);
				s.updateCall(dynamo ? bothEndsDynamo : bothEndsChariot, e1);
				dynamo = false;
			});

	private final ModifiableCallout<HeadMarkerEvent> fiveFingeredPunishment = new ModifiableCallout<>("Five-Fingered Punishment", "Stack on {event.target}");

	@HandleEvents
	public void fiveFingeredPunishment(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x3E)) {
			context.accept(fiveFingeredPunishment.getModified(event));
		}
	}

	// ロングエンドは前半だけノックバック付きなので、中間フェーズを見て切り替える
	@HandleEvents
	public void intermission(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x2CC7)) {
			seenIntermission = true;
		}
	}

	@HandleEvents
	public void longEndCollect(EventContext context, TetherEvent event) {
		if (event.tetherIdMatches(LONG_END_TETHER)) {
			longEndTethered.addAll(event.getTargets());
		}
	}

	private final ModifiableCallout<TetherEvent> longEndLaser = new ModifiableCallout<>("Long End: On You", "Laser on You");
	private final ModifiableCallout<TetherEvent> longEndKnockback = new ModifiableCallout<>("Long End: On You (Knockback)", "Knockback Laser on You");
	private final ModifiableCallout<TetherEvent> longEndAvoid = new ModifiableCallout<>("Long End: Not Targeted", "Avoid Tethers");

	// 線は1本か2本なので、揃うのを待ってから判定する
	@AutoFeed
	private final SequentialTrigger<BaseEvent> longEndSq = SqtTemplates.sq(5_000,
			TetherEvent.class, te -> te.tetherIdMatches(LONG_END_TETHER),
			(e1, s) -> {
				s.waitMs(500);
				boolean onPlayer = longEndTethered.stream().anyMatch(XivCombatant::isThePlayer);
				longEndTethered.clear();
				if (!onPlayer) {
					s.updateCall(longEndAvoid, e1);
				}
				else {
					s.updateCall(seenIntermission ? longEndLaser : longEndKnockback, e1);
				}
			});

}
