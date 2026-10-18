package gg.xp.xivsupport.triggers.dtdungeon;

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
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.RawXivCombatantInfo;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcAbilityUsedCallout;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// cactbot（Apache License 2.0）の ui/raidboss/data/07-dt/dungeon/alexandria.ts をもとに移植
@CalloutRepo(name = "Alexandria", duty = KnownDuty.Alexandria)
public class Alexandria extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public Alexandria(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.Alexandria);
	}

	// Interferon R / C の BNpcNameId (10進)
	private static final long interferonRNameId = 12842;
	private static final long interferonCNameId = 12843;
	private static final int interferonCount = 5;

	private final List<String> interferonCalls = new ArrayList<>();
	private boolean disinfectOnMe;
	// 0xDA はアマルガムとエリミネーターで別の意味になるので、どちらの戦闘か覚えておく
	private boolean eliminatorFight;

	@HandleEvents
	public void reset(PullStartedEvent unused) {
		interferonCalls.clear();
		disinfectOnMe = false;
	}

	// 1ボス目（Antivirus X）

	@NpcCastCallout(0x8E1A)
	private final ModifiableCallout<AbilityCastStart> immuneResponseFront = ModifiableCallout.durationBasedCall("Immune Response (Front)", "Sides");

	@NpcCastCallout(0x8E1C)
	private final ModifiableCallout<AbilityCastStart> immuneResponseBack = ModifiableCallout.durationBasedCall("Immune Response (Back)", "Front");

	private final ModifiableCallout<RawAddCombatantEvent> interferon = new ModifiableCallout<>("Interferon", "{calls}", 15_000);

	@HandleEvents
	public void interferonAdded(EventContext context, RawAddCombatantEvent event) {
		RawXivCombatantInfo info = event.getFullInfo();
		long nameId = info != null ? info.getBnpcNameId() : event.getEntity().getbNpcNameId();
		if (nameId == interferonCNameId) {
			interferonCalls.add("Avoid");
		}
		else if (nameId == interferonRNameId) {
			interferonCalls.add("In");
		}
		else {
			return;
		}
		// 5体そろった時点でまとめて順番を読み上げる
		if (interferonCalls.size() == interferonCount) {
			context.accept(interferon.getModified(event, Map.of("calls", String.join(" then ", interferonCalls))));
			interferonCalls.clear();
		}
	}

	@PlayerHeadmarker(0x158)
	private final ModifiableCallout<HeadMarkerEvent> disinfection = new ModifiableCallout<>("Disinfection", "Cleave on You, Don't Stack");

	@HandleEvents
	public void disinfectionTracker(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x158) && event.getTarget().isThePlayer()) {
			disinfectOnMe = true;
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> quarantine = new ModifiableCallout<>("Quarantine", "Stack on {event.target}");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> quarantineSq = SqtTemplates.sq(5_000,
			HeadMarkerEvent.class, hm -> hm.markerIdMatches(0x3E),
			(e1, s) -> {
				// 頭割りと同時に付く Disinfection の判定を待つ
				s.waitMs(500);
				if (!disinfectOnMe) {
					s.updateCall(quarantine, e1);
				}
				disinfectOnMe = false;
			});

	@NpcCastCallout(0x8E23)
	private final ModifiableCallout<AbilityCastStart> cytolysis = ModifiableCallout.durationBasedCall("Cytolysis", "Raidwide");

	// 2ボス目（Amalgam）

	@HandleEvents
	public void bossTracker(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x8DF1, 0x8DE3, 0x98E0, 0x98E2, 0x8DE7, 0x8DEB, 0x9955)) {
			eliminatorFight = false;
		}
		else if (event.abilityIdMatches(0x8F9D, 0x9951, 0x985F, 0x9946, 0x9ABF, 0x9AC0, 0x8FBA, 0x8FBB)) {
			eliminatorFight = true;
		}
	}

	@NpcCastCallout(0x8DF1)
	private final ModifiableCallout<AbilityCastStart> electrowave = ModifiableCallout.durationBasedCall("Electrowave", "Raidwide");

	@NpcCastCallout(0x8DE3)
	private final ModifiableCallout<AbilityCastStart> disassembly = ModifiableCallout.durationBasedCall("Disassembly", "Raidwide");

	@NpcCastCallout(0x98E0)
	private final ModifiableCallout<AbilityCastStart> supercellMatrixTriangle = ModifiableCallout.durationBasedCall("Supercell Matrix (Triangle)", "Out of Lightning Triangle");

	@NpcCastCallout(value = 0x98E2, suppressMs = 1_000)
	private final ModifiableCallout<AbilityCastStart> supercellMatrixLasers = ModifiableCallout.durationBasedCall("Supercell Matrix (Lasers)", "Avoid Wall Lasers");

	@NpcCastCallout(0x8DE7)
	private final ModifiableCallout<AbilityCastStart> centralizedCurrent = ModifiableCallout.durationBasedCall("Centralized Current", "Sides");

	@NpcCastCallout(0x8DEB)
	private final ModifiableCallout<AbilityCastStart> splitCurrent = ModifiableCallout.durationBasedCall("Split Current", "Middle");

	@PlayerHeadmarker(0x8B)
	private final ModifiableCallout<HeadMarkerEvent> staticSpark = new ModifiableCallout<>("Static Spark", "Spread");

	private final ModifiableCallout<HeadMarkerEvent> amalgamight = new ModifiableCallout<>("Amalgamight", "Buster on {event.target}");
	private final ModifiableCallout<HeadMarkerEvent> electray = new ModifiableCallout<>("Electray", "Spread");

	@HandleEvents
	public void markerDA(EventContext context, HeadMarkerEvent event) {
		if (!event.markerIdMatches(0xDA)) {
			return;
		}
		if (eliminatorFight) {
			if (event.getTarget().isThePlayer()) {
				context.accept(electray.getModified(event));
			}
		}
		else {
			context.accept(amalgamight.getModified(event));
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> superbolt = new ModifiableCallout<>("Superbolt", "Stack on {event.target}");

	@HandleEvents
	public void superboltMarker(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0xA1)) {
			context.accept(superbolt.getModified(event));
		}
	}

	@NpcCastCallout(0x9955)
	private final ModifiableCallout<AbilityCastStart> ternaryCharge = ModifiableCallout.durationBasedCall("Ternary Charge", "Out then In");

	// 3ボス目（Eliminator）

	@NpcCastCallout(0x8F9D)
	private final ModifiableCallout<AbilityCastStart> disruption = ModifiableCallout.durationBasedCall("Disruption", "Raidwide");

	@NpcCastCallout(0x9951)
	private final ModifiableCallout<AbilityCastStart> partitionLeft = ModifiableCallout.durationBasedCall("Partition (Left Cleave)", "Right");

	// 初回だけ 985F が使われ、2回目以降の右側は 9946 になる
	@NpcCastCallout({0x985F, 0x9946})
	private final ModifiableCallout<AbilityCastStart> partitionRight = ModifiableCallout.durationBasedCall("Partition (Right Cleave)", "Left");

	@NpcCastCallout(0x9ABF)
	private final ModifiableCallout<AbilityCastStart> terminate = ModifiableCallout.durationBasedCall("Terminate", "Avoid Hand Laser");

	@NpcCastCallout(value = 0x9AC0, suppressMs = 1_000)
	private final ModifiableCallout<AbilityCastStart> haloOfDestruction = ModifiableCallout.durationBasedCall("Halo of Destruction", "Get Under Elimbit");

	// 頭割りレーザーには頭上マーカーが無く、対象者への即時アクションで代わりに判定する
	@NpcAbilityUsedCallout(0x8FAA)
	private final ModifiableCallout<AbilityUsedEvent> overexposure = new ModifiableCallout<>("Overexposure", "Stack on {event.target}");

	private final ModifiableCallout<AbilityUsedEvent> holoArk = new ModifiableCallout<>("Holo Ark", "Raidwide");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> holoArkSq = SqtTemplates.sq(15_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x8FB5),
			(e1, s) -> {
				// 8FB5 は予備動作で、実際のダメージ (8FB6) は約10秒後
				s.waitMs(5_000);
				s.updateCall(holoArk, e1);
			});

	@NpcCastCallout(0x8FBA)
	private final ModifiableCallout<AbilityCastStart> impact = ModifiableCallout.durationBasedCall("Impact", "Knockback");

	@PlayerHeadmarker(0x216)
	private final ModifiableCallout<HeadMarkerEvent> lightOfSalvation = new ModifiableCallout<>("Light of Salvation", "Spread");

	@NpcAbilityUsedCallout(0x8FB2)
	private final ModifiableCallout<AbilityUsedEvent> lightOfDevotion = new ModifiableCallout<>("Light of Devotion", "Stack on {event.target}");

	@NpcCastCallout(0x8FBB)
	private final ModifiableCallout<AbilityCastStart> elimination = ModifiableCallout.durationBasedCall("Elimination", "Dodge Multiple Lasers");

}
