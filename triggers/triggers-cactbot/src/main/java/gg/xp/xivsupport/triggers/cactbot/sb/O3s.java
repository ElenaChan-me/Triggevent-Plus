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
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerStatusCallout;
import gg.xp.xivsupport.models.Position;
import gg.xp.xivsupport.models.XivCombatant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/raid/o3s.ts をもとに移植
@CalloutRepo(name = "Deltascape V30 Savage", duty = KnownDuty.O7S)
public class O3s extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long stackMarkerId = 0x64;
	private static final long preyMarkerId = 0x65;
	private static final int libraryPhase = 3;
	private static final int mindjackDurationMs = 8_000;
	private static final long whiteFlameNameId = 5626;
	private static final long greatDragonNameId = 6724;
	private static final long apandaNameId = 6056;
	private static final long whiteFlameNpcId = 7399;
	private static final long greatDragonNpcId = 7400;
	private static final long apandaNpcId = 7401;
	private static final long ironGiantNameId = 5636;
	private static final long soulReaperNameId = 5634;
	private static final int soulReaperCount = 4;
	// 死神は対角線上に左右対称に並ぶので、行と列を一次元の問題として扱える
	private static final double reaperGridOffset = 15;
	private static final double reaperGridSpacing = 10;
	private static final double reaperOutsideThreshold = 17;

	private XivState state;

	public O3s(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.O7S);
	}

	private int phase;
	private boolean seenHolyThisPhase;
	private final List<XivCombatant> holyTargets = new ArrayList<>();
	private Long librarySpellbladeMe;
	private boolean librarySpellbladePrinted;
	private int bookCount;
	private final List<Position> reapers = new ArrayList<>();

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		phase = 0;
		seenHolyThisPhase = false;
		holyTargets.clear();
		librarySpellbladeMe = null;
		librarySpellbladePrinted = false;
		bookCount = 0;
		reapers.clear();
	}

	@HandleEvents
	public void panelSwap(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x2304)) {
			phase++;
			seenHolyThisPhase = false;
		}
	}

	@NpcCastCallout(0x22EB)
	private final ModifiableCallout<AbilityCastStart> criticalHit = ModifiableCallout.durationBasedCall("Critical Hit", "Buster on {event.target}");

	@NpcCastCallout(0x22F6)
	private final ModifiableCallout<AbilityCastStart> dimensionalWave = ModifiableCallout.durationBasedCall("Dimensional Wave", "Raidwide");

	// 図書館フェーズ最後のワルツを無視するため、ホーリー剣の実行を記録する
	@HandleEvents
	public void spellbladeHolyUsed(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x22EF) && !event.getSource().isPc()) {
			holyTargets.clear();
			seenHolyThisPhase = true;
		}
	}

	// 通常のホーリー剣

	private final ModifiableCallout<HeadMarkerEvent> holyStackOnYou = new ModifiableCallout<>("Spellblade Holy (Stack on You)", "Stack on You");
	private final ModifiableCallout<HeadMarkerEvent> holySpread = new ModifiableCallout<>("Spellblade Holy (Prey)", "Spread, Others Stack on {stack}");
	private final ModifiableCallout<HeadMarkerEvent> holyStackOn = new ModifiableCallout<>("Spellblade Holy (Unmarked)", "Stack on {stack}");

	@HandleEvents
	public void holyMarkerCollect(EventContext context, HeadMarkerEvent event) {
		if (phase != libraryPhase && event.markerIdMatches(stackMarkerId, preyMarkerId)) {
			holyTargets.add(event.getTarget());
		}
	}

	@AutoFeed
	private final SequentialTrigger<BaseEvent> spellbladeHolySq = SqtTemplates.sq(5_000,
			HeadMarkerEvent.class, hm -> phase != libraryPhase && hm.markerIdMatches(stackMarkerId, preyMarkerId),
			(e1, s) -> {
				s.waitMs(300);
				if (holyTargets.size() < 2) {
					holyTargets.clear();
					return;
				}
				// 頭割りの対象はログ上で2番目に付いたマーカーになる
				XivCombatant stackTarget = holyTargets.get(1);
				boolean marked = holyTargets.stream().anyMatch(XivCombatant::isThePlayer);
				holyTargets.clear();
				s.setParam("stack", stackTarget);
				if (stackTarget.isThePlayer()) {
					s.updateCall(holyStackOnYou, e1);
				}
				else if (marked) {
					s.updateCall(holySpread, e1);
				}
				else {
					s.updateCall(holyStackOn, e1);
				}
			});

	// 図書館フェーズのホーリー剣（頭割り2・ターゲット4・無職2）

	private final ModifiableCallout<HeadMarkerEvent> librarySouthStackOnYou = new ModifiableCallout<>("Library Spellblade (Stack on You)", "Go South: Stack on You");
	private final ModifiableCallout<HeadMarkerEvent> libraryNorth = new ModifiableCallout<>("Library Spellblade (Prey)", "Go North");
	private final ModifiableCallout<HeadMarkerEvent> librarySouthStackOnFriend = new ModifiableCallout<>("Library Spellblade (Unmarked)", "Go South: Stack on Friend");

	private ModifiableCallout<HeadMarkerEvent> libraryCallout() {
		if (librarySpellbladeMe == null) {
			return librarySouthStackOnFriend;
		}
		return librarySpellbladeMe == stackMarkerId ? librarySouthStackOnYou : libraryNorth;
	}

	// 自分にマーカーが付いたら即座に呼ぶ
	@HandleEvents
	public void librarySpellblade(EventContext context, HeadMarkerEvent event) {
		if (phase != libraryPhase || !event.markerIdMatches(stackMarkerId, preyMarkerId) || !event.getTarget().isThePlayer()) {
			return;
		}
		librarySpellbladeMe = event.getMarkerId();
		if (!librarySpellbladePrinted) {
			librarySpellbladePrinted = true;
			context.accept(libraryCallout().getModified(event));
		}
	}

	// 死んでいる人がいるとマーカーの数が揃わないので、数えずに少し待ってから判定する
	@AutoFeed
	private final SequentialTrigger<BaseEvent> librarySpellbladeSq = SqtTemplates.sq(5_000,
			HeadMarkerEvent.class, hm -> phase == libraryPhase && hm.markerIdMatches(stackMarkerId, preyMarkerId),
			(e1, s) -> {
				s.waitMs(500);
				if (!librarySpellbladePrinted) {
					librarySpellbladePrinted = true;
					s.updateCall(libraryCallout(), e1);
				}
			});

	// マインドジャック

	@PlayerStatusCallout(0x510)
	private final ModifiableCallout<BuffApplied> rightFace = new ModifiableCallout<BuffApplied>("Right Face", "Mindjack: Right", mindjackDurationMs).statusIcon(0x510);

	@PlayerStatusCallout(0x50D)
	private final ModifiableCallout<BuffApplied> forwardMarch = new ModifiableCallout<BuffApplied>("Forward March", "Mindjack: Forward", mindjackDurationMs).statusIcon(0x50D);

	@PlayerStatusCallout(0x50F)
	private final ModifiableCallout<BuffApplied> leftFace = new ModifiableCallout<BuffApplied>("Left Face", "Mindjack: Left", mindjackDurationMs).statusIcon(0x50F);

	@PlayerStatusCallout(0x50E)
	private final ModifiableCallout<BuffApplied> aboutFace = new ModifiableCallout<BuffApplied>("About Face", "Mindjack: Back", mindjackDurationMs).statusIcon(0x50E);

	@NpcCastCallout(0x22F7)
	private final ModifiableCallout<AbilityCastStart> ribbit = ModifiableCallout.durationBasedCall("Ribbit", "Get Behind");

	@NpcCastCallout(0x22F9)
	private final ModifiableCallout<AbilityCastStart> oink = ModifiableCallout.durationBasedCall("Oink", "Dorito Stack");

	@NpcCastCallout(0x22F8)
	private final ModifiableCallout<AbilityCastStart> squelch = ModifiableCallout.durationBasedCall("Squelch", "Look Away");

	// 雑魚

	private final ModifiableCallout<RawAddCombatantEvent> killAdd = new ModifiableCallout<>("White Flame/Great Dragon/Apanda Spawn", "Kill {add}");
	private final ModifiableCallout<RawAddCombatantEvent> ironGiant = new ModifiableCallout<>("Iron Giant Spawn", "Kill Giant and Ninjas");

	private final ModifiableCallout<RawAddCombatantEvent> reaperNwOutside = new ModifiableCallout<>("Soul Reaper (NW Outside)", "Northwest Outside");
	private final ModifiableCallout<RawAddCombatantEvent> reaperNwInside = new ModifiableCallout<>("Soul Reaper (NW Inside)", "Northwest Inside");
	private final ModifiableCallout<RawAddCombatantEvent> reaperSeInside = new ModifiableCallout<>("Soul Reaper (SE Inside)", "Southeast Inside");
	private final ModifiableCallout<RawAddCombatantEvent> reaperSeOutside = new ModifiableCallout<>("Soul Reaper (SE Outside)", "Southeast Outside");
	private final ModifiableCallout<RawAddCombatantEvent> reaperUnknown = new ModifiableCallout<>("Soul Reaper (Unknown)", "Unknown");

	@HandleEvents
	public void addedCombatant(EventContext context, RawAddCombatantEvent event) {
		XivCombatant cbt = event.getEntity();
		long nameId = cbt.getbNpcNameId();
		// 開幕に別IDのアパンダが大量に湧くので、基本IDも合わせて確認する
		if ((nameId == whiteFlameNameId || nameId == greatDragonNameId || nameId == apandaNameId)
				&& cbt.npcIdMatches(whiteFlameNpcId, greatDragonNpcId, apandaNpcId)) {
			context.accept(killAdd.getModified(event, Map.of("add", cbt)));
		}
		else if (nameId == ironGiantNameId) {
			context.accept(ironGiant.getModified(event));
		}
		else if (nameId == soulReaperNameId) {
			Position pos = cbt.getPos();
			if (pos == null) {
				return;
			}
			reapers.add(pos);
			if (reapers.size() == soulReaperCount) {
				context.accept(soulReaperCall().getModified(event));
			}
		}
	}

	private ModifiableCallout<RawAddCombatantEvent> soulReaperCall() {
		Set<Integer> safeSpots = new TreeSet<>(List.of(0, 1, 2, 3));
		for (Position pos : reapers) {
			double x = pos.x();
			double y = pos.y();
			// 中央付近の1体は判定に使わない
			if (Math.abs(x) < 1 && Math.abs(y) < 1) {
				continue;
			}
			if (Math.abs(x) < reaperOutsideThreshold) {
				safeSpots.remove(toGridIndex(x));
			}
			if (Math.abs(y) < reaperOutsideThreshold) {
				safeSpots.remove(toGridIndex(y));
			}
		}
		if (safeSpots.size() != 1) {
			return reaperUnknown;
		}
		return switch (safeSpots.iterator().next()) {
			case 0 -> reaperNwOutside;
			case 1 -> reaperNwInside;
			case 2 -> reaperSeInside;
			default -> reaperSeOutside;
		};
	}

	private static int toGridIndex(double coord) {
		return (int) Math.round((coord + reaperGridOffset) / reaperGridSpacing);
	}

	// クイーンズワルツ

	private final ModifiableCallout<AbilityCastStart> books = ModifiableCallout.durationBasedCall("The Queen's Waltz: Books", "Books, One Per Square");
	private final ModifiableCallout<AbilityCastStart> booksHammer = ModifiableCallout.durationBasedCall("The Queen's Waltz: Books (Magic Hammer)", "Books and Magic Hammer");

	@HandleEvents
	public void waltzBooks(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x230E)) {
			return;
		}
		// 図書館フェーズを締めくくるホーリー剣の後のワルツは呼ばない
		if (phase == libraryPhase && seenHolyThisPhase) {
			return;
		}
		bookCount++;
		// 2回目はアパンダの魔法のハンマーが重なる
		context.accept((bookCount == 2 ? booksHammer : books).getModified(event));
	}

	@NpcCastCallout(0x2306)
	private final ModifiableCallout<AbilityCastStart> waltzClock = ModifiableCallout.durationBasedCall("The Queen's Waltz: Clock", "Clock");

	@NpcCastCallout(0x230A)
	private final ModifiableCallout<AbilityCastStart> waltzCrystal = ModifiableCallout.durationBasedCall("The Queen's Waltz: Crystal Square", "Be on Blue Square");

	@NpcCastCallout(0x2308)
	private final ModifiableCallout<AbilityCastStart> waltzTethers = ModifiableCallout.durationBasedCall("The Queen's Waltz: Tethers", "Tethers");

	private final ModifiableCallout<TetherEvent> breakTether = new ModifiableCallout<>("The Queen's Waltz: Tether on You", "Break Tether ({partner})");

	@HandleEvents
	public void waltzTether(EventContext context, TetherEvent event) {
		if (!event.tetherIdMatches(0x12) || !event.eitherTargetMatches(XivCombatant::isThePlayer)) {
			return;
		}
		XivCombatant partner = event.getTargetMatching(cbt -> !cbt.isThePlayer());
		if (partner != null) {
			context.accept(breakTether.getModified(event, Map.of("partner", partner)));
		}
	}

}
