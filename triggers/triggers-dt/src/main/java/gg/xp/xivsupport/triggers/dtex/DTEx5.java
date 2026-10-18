package gg.xp.xivsupport.triggers.dtex;

import gg.xp.reevent.events.BaseEvent;
import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.AutoFeed;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.*;
import gg.xp.xivdata.data.duties.*;
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
import gg.xp.xivsupport.gui.util.HasFriendlyName;
import gg.xp.xivsupport.models.ArenaPos;
import gg.xp.xivsupport.models.ArenaSector;
import gg.xp.xivsupport.models.Position;
import gg.xp.xivsupport.models.XivCombatant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// cactbot（Apache License 2.0）の ui/raidboss/data/07-dt/trial/necron-ex.ts をもとに移植
@CalloutRepo(name = "EX5", duty = KnownDuty.DtEx5)
public class DTEx5 extends AutoChildEventHandler implements FilteredEventHandler {

	private static final Logger log = LoggerFactory.getLogger(DTEx5.class);

	private static final Position arenaCenter = Position.of2d(100, 100);

	private XivState state;

	public DTEx5(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.DtEx5);
	}

	private enum ReapingSafeDir implements HasFriendlyName {
		OUT("Out"),
		IN("In"),
		MIDDLE("Middle"),
		SIDES("Sides");

		private final String friendlyName;

		ReapingSafeDir(String friendlyName) {
			this.friendlyName = friendlyName;
		}

		@Override
		public String getFriendlyName() {
			return friendlyName;
		}

		static ReapingSafeDir forMarker(long markerId) {
			return switch ((int) markerId) {
				case 0x025C -> OUT;
				case 0x025D -> IN;
				case 0x025E -> MIDDLE;
				case 0x025F -> SIDES;
				default -> null;
			};
		}
	}

	private enum SpecterRow implements HasFriendlyName {
		NORTH("North Row"),
		MIDDLE("Middle Row"),
		SOUTH("South Row");

		private final String friendlyName;

		SpecterRow(String friendlyName) {
			this.friendlyName = friendlyName;
		}

		@Override
		public String getFriendlyName() {
			return friendlyName;
		}
	}

	private int reapingCounter;
	private final List<ReapingSafeDir> reapingSafeDirs = new ArrayList<>();
	private final List<XivCombatant> loomingSpecters = new ArrayList<>();
	private int macabreTowerCount;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		reapingCounter = 0;
		reapingSafeDirs.clear();
		loomingSpecters.clear();
		macabreTowerCount = 0;
	}

	private final ModifiableCallout<HeadMarkerEvent> blueShockwave = new ModifiableCallout<>("Blue Shockwave", "Tankbuster");

	private final RepeatSuppressor blueShockwaveSupp = new RepeatSuppressor(Duration.ofSeconds(2));

	@HandleEvents
	public void blueShockwave(EventContext context, HeadMarkerEvent event) {
		if (!event.markerIdMatches(0x0267)) {
			return;
		}
		// マーカーの対象はボス側で、実際に狙われるプレイヤーは副対象に入る
		if (event.eitherTargetMatches(XivCombatant::isThePlayer) || state.playerJobMatches(Job::isTank)) {
			if (blueShockwaveSupp.check(event)) {
				context.accept(blueShockwave.getModified(event));
			}
		}
	}

	private final ModifiableCallout<AbilityCastStart> fearOfDeath = ModifiableCallout.durationBasedCall("Fear of Death", "Raidwide");
	private final ModifiableCallout<?> fearOfDeathBait = new ModifiableCallout<>("Fear of Death: Bait", "Bait Hand");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> fearOfDeathSq = SqtTemplates.sq(30_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xAE06),
			(e1, s) -> {
				s.updateCall(fearOfDeath, e1);
				long waitMs = e1.getEstimatedRemainingDuration().toMillis() - 2_000;
				if (waitMs > 0) {
					s.waitMs(waitMs);
				}
				s.updateCall(fearOfDeathBait);
			});

	@NpcCastCallout(0xAE09)
	private final ModifiableCallout<AbilityCastStart> coldGripWest = ModifiableCallout.durationBasedCall("Cold Grip (West Safe)", "Middle then West");

	@NpcCastCallout(0xAE0A)
	private final ModifiableCallout<AbilityCastStart> coldGripEast = ModifiableCallout.durationBasedCall("Cold Grip (East Safe)", "Middle then East");

	private final ModifiableCallout<AbilityCastStart> mementoMoriWest = ModifiableCallout.durationBasedCall("Memento Mori (Light West)", "Light West then Spread");
	private final ModifiableCallout<AbilityCastStart> mementoMoriEast = ModifiableCallout.durationBasedCall("Memento Mori (Light East)", "Light East then Spread");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> mementoMoriSq = SqtTemplates.selfManagedMultiInvocation(5_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xAE15, 0xAE16),
			(e1, s, index) -> {
				// 2回目は元の cactbot でも呼んでいないので合わせる
				if (index == 1) {
					return;
				}
				s.updateCall(e1.abilityIdMatches(0xAE15) ? mementoMoriWest : mementoMoriEast, e1);
			});

	private final ModifiableCallout<HeadMarkerEvent> soulReaping = new ModifiableCallout<>("Soul Reaping: Safe Spot", "{dir}");
	private final ModifiableCallout<HeadMarkerEvent> soulReapingStored = new ModifiableCallout<>("Soul Reaping: Stored Safe Spot", "Stored {dir}");

	@HandleEvents
	public void reapingMarkers(EventContext context, HeadMarkerEvent event) {
		ReapingSafeDir dir = ReapingSafeDir.forMarker(event.getMarkerId());
		if (dir == null) {
			return;
		}
		reapingSafeDirs.add(dir);
		ReapingSafeDir first = reapingSafeDirs.get(0);
		if (reapingCounter == 1) {
			context.accept(soulReaping.getModified(event, Map.of("dir", first)));
		}
		else if (reapingCounter == 2) {
			context.accept(soulReapingStored.getModified(event, Map.of("dir", first)));
		}
	}

	private final ModifiableCallout<AbilityCastStart> twofoldBlight = ModifiableCallout.durationBasedCall("Twofold Blight", "{dir}, Light Parties");
	private final ModifiableCallout<AbilityCastStart> twofoldBlightUnknown = ModifiableCallout.durationBasedCall("Twofold Blight (Unknown Safe Spot)", "Light Parties");
	private final ModifiableCallout<AbilityCastStart> fourfoldBlight = ModifiableCallout.durationBasedCall("Fourfold Blight", "{dir}, Partners");
	private final ModifiableCallout<AbilityCastStart> fourfoldBlightUnknown = ModifiableCallout.durationBasedCall("Fourfold Blight (Unknown Safe Spot)", "Partners");

	@HandleEvents
	public void reapingCasts(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0xAE0C, 0xAE14)) {
			reapingCounter++;
		}
		else if (event.abilityIdMatches(0xAE0D, 0xAE0E)) {
			boolean twofold = event.abilityIdMatches(0xAE0D);
			if (reapingSafeDirs.isEmpty()) {
				context.accept((twofold ? twofoldBlightUnknown : fourfoldBlightUnknown).getModified(event));
			}
			else {
				context.accept((twofold ? twofoldBlight : fourfoldBlight).getModified(event, Map.of("dir", reapingSafeDirs.get(0))));
			}
			reapingSafeDirs.clear();
		}
	}

	@PlayerHeadmarker(0x0266)
	private final ModifiableCallout<HeadMarkerEvent> endsEmbrace = new ModifiableCallout<>("The End's Embrace", "Drop Hand then Bait Hand");

	@NpcCastCallout(0xAE18)
	private final ModifiableCallout<AbilityCastStart> grandCross = ModifiableCallout.durationBasedCall("Grand Cross", "Raidwide");

	private final ModifiableCallout<AbilityUsedEvent> grandCrossBait = new ModifiableCallout<>("Grand Cross: Bait Puddles", "Bait Puddles");
	private final ModifiableCallout<?> grandCrossBaitEnd = new ModifiableCallout<>("Grand Cross: Bait Puddles then Intercards", "Bait Puddles then Intercardinals");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> grandCrossPuddles = SqtTemplates.sq(30_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0xAE18),
			(e1, s) -> {
				s.updateCall(grandCrossBait, e1);
				s.waitMs(26_500);
				s.updateCall(grandCrossBaitEnd);
			});

	private final ModifiableCallout<HeadMarkerEvent> grandCrossSpread = new ModifiableCallout<>("Grand Cross: Spread", "Spread");
	private final ModifiableCallout<HeadMarkerEvent> grandCrossTower = new ModifiableCallout<>("Grand Cross: Tower", "Soak Tower");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> grandCrossSpreadTower = SqtTemplates.sq(10_000,
			HeadMarkerEvent.class, hme -> hme.markerIdMatches(0x0263),
			(e1, s) -> {
				List<HeadMarkerEvent> markers = new ArrayList<>();
				markers.add(e1);
				markers.addAll(s.waitEvents(3, HeadMarkerEvent.class, hme -> hme.markerIdMatches(0x0263)));
				markers.stream().filter(hme -> hme.getTarget().isThePlayer()).findAny().ifPresentOrElse(
						mine -> s.updateCall(grandCrossSpread, mine),
						() -> s.updateCall(grandCrossTower, markers.get(markers.size() - 1)));
			});

	@NpcCastCallout(0xAE1F)
	private final ModifiableCallout<AbilityCastStart> neutronRing = ModifiableCallout.durationBasedCall("Neutron Ring", "Raidwide");

	@NpcCastCallout(0xAE24)
	private final ModifiableCallout<AbilityCastStart> darknessOfEternity = ModifiableCallout.durationBasedCall("Darkness of Eternity", "Raidwide");

	@PlayerStatusCallout(0xD88)
	private final ModifiableCallout<BuffApplied> slow = ModifiableCallout.<BuffApplied>durationBasedCall("Slow", "Cleanse Slow").statusIcon(0xD88);

	@HandleEvents
	public void loomingSpecterTethers(EventContext context, TetherEvent event) {
		if (event.tetherIdMatches(0x0066)) {
			loomingSpecters.add(event.getSource());
		}
	}

	// 位置は線が付いた直後だとまだ更新されていないことがあるので、使う時点で最新の値を見る
	private List<SpecterRow> loomingSpecterRows() {
		List<SpecterRow> rows = new ArrayList<>();
		for (XivCombatant specter : loomingSpecters) {
			Position pos = state.getLatestCombatantData(specter).getPos();
			if (pos == null) {
				log.warn("Looming Specter: missing position for {}", specter);
				continue;
			}
			if (pos.y() < 99) {
				rows.add(SpecterRow.NORTH);
			}
			else if (pos.y() > 101) {
				rows.add(SpecterRow.SOUTH);
			}
			else {
				rows.add(SpecterRow.MIDDLE);
			}
		}
		return rows;
	}

	private final ModifiableCallout<AbilityCastStart> specterOfDeathFirst = ModifiableCallout.durationBasedCall("Specter of Death: Safe Row", "{row}, Spread");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> specterOfDeathSq = SqtTemplates.selfManagedMultiInvocation(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xAE3E),
			(e1, s, index) -> {
				if (index != 0) {
					return;
				}
				s.waitThenRefreshCombatants(1_000);
				Set<SpecterRow> safeRows = EnumSet.allOf(SpecterRow.class);
				loomingSpecterRows().forEach(safeRows::remove);
				loomingSpecters.clear();
				if (safeRows.size() != 1) {
					log.error("Specter of Death: invalid rows {}", safeRows);
					return;
				}
				s.setParam("row", safeRows.iterator().next());
				s.updateCall(specterOfDeathFirst, e1);
			});

	private final ModifiableCallout<AbilityUsedEvent> endsEmbraceBait = new ModifiableCallout<>("The End's Embrace: Bait", "Bait Hand then Dodge");

	@HandleEvents
	public void endsEmbraceBait(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0xAE36) && event.getTarget().isThePlayer()) {
			context.accept(endsEmbraceBait.getModified(event));
		}
	}

	private static final long cropCircleStatus = 0x808;
	private static final long cropCircleFirstStacks = 0x3B8;
	private static final int cropCircleCount = 4;
	// これより低い位置にいるものが最初に発動する
	private static final double cropCircleBottomZ = 5;

	private final ModifiableCallout<AbilityCastStart> secondSeason = ModifiableCallout.durationBasedCall("The Second Season", "{dir1} then {dir2} then {dir3} then {dir4}, Light Parties");
	private final ModifiableCallout<AbilityCastStart> fourthSeason = ModifiableCallout.durationBasedCall("The Fourth Season", "{dir1} then {dir2} then {dir3} then {dir4}, Partners");

	private static boolean isCropCircleBuff(BuffApplied ba) {
		long stacks = ba.getRawStacks();
		return ba.buffIdMatches(cropCircleStatus) && stacks >= cropCircleFirstStacks && stacks < cropCircleFirstStacks + cropCircleCount;
	}

	@AutoFeed
	private final SequentialTrigger<BaseEvent> cropCircleSq = SqtTemplates.sq(60_000,
			BuffApplied.class, DTEx5::isCropCircleBuff,
			(e1, s) -> {
				Map<Long, XivCombatant> actors = new HashMap<>();
				actors.put(e1.getRawStacks(), e1.getTarget());
				while (actors.size() < cropCircleCount) {
					BuffApplied next = s.waitEvent(BuffApplied.class, DTEx5::isCropCircleBuff);
					actors.put(next.getRawStacks(), next.getTarget());
				}
				s.waitThenRefreshCombatants(100);
				List<Map.Entry<Long, XivCombatant>> bottom = actors.entrySet().stream()
						.filter(entry -> {
							Position pos = state.getLatestCombatantData(entry.getValue()).getPos();
							return pos != null && pos.z() < cropCircleBottomZ;
						})
						.toList();
				if (bottom.size() != 1) {
					log.error("Crop Circle: wrong bottom actor count {}", bottom);
					return;
				}
				int offset = (int) (bottom.get(0).getKey() - cropCircleFirstStacks);
				List<ReapingSafeDir> doubled = new ArrayList<>(reapingSafeDirs);
				doubled.addAll(reapingSafeDirs);
				if (doubled.size() < offset + cropCircleCount) {
					log.error("Crop Circle: not enough safe dirs {}", reapingSafeDirs);
					return;
				}
				List<ReapingSafeDir> order = doubled.subList(offset, offset + cropCircleCount);

				AbilityCastStart season = s.waitEvent(AbilityCastStart.class, acs -> acs.abilityIdMatches(0xB06F, 0xB070));
				for (int i = 0; i < cropCircleCount; i++) {
					s.setParam("dir" + (i + 1), order.get(i));
				}
				s.updateCall(season.abilityIdMatches(0xB06F) ? secondSeason : fourthSeason, season);
				reapingSafeDirs.clear();
			});

	private final ModifiableCallout<AbilityCastStart> circleOfLives = ModifiableCallout.durationBasedCall("Circle of Lives", "{safe}");
	private final ModifiableCallout<AbilityCastStart> circleOfLivesDodge = ModifiableCallout.durationBasedCall("Circle of Lives: Dodge Hand After", "{safe} then Dodge Hand");
	private final ModifiableCallout<AbilityCastStart> circleOfLivesWait = ModifiableCallout.durationBasedCall("Circle of Lives: Wait for Hand", "Wait for Hand then {safe}");
	private final ModifiableCallout<AbilityCastStart> circleOfLivesLean = ModifiableCallout.durationBasedCall("Circle of Lives: Lean", "{safe}, Lean {to}");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> circleOfLivesSq = SqtTemplates.selfManagedMultiInvocation(5_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xAE38),
			(e1, s, index) -> {
				int count = index + 1;
				s.waitThenRefreshCombatants(200);
				Position pos = state.getLatestCombatantData(e1.getSource()).getPos();
				if (pos == null) {
					log.error("Circle of Lives: missing position for {}", e1.getSource());
					return;
				}
				boolean middle = Math.abs(pos.x() - 100) < 1;
				ArenaSector safe = middle ? ArenaSector.CENTER : ArenaPos.combatantFacing(arenaCenter.facing(pos));
				s.setParam("safe", safe);

				List<SpecterRow> rows = loomingSpecterRows();
				// 最初の5回は手の列と輪の列が重なる場合だけ注意が必要
				if (count <= 5) {
					SpecterRow handRow = rows.isEmpty() ? null : rows.get(0);
					SpecterRow circleRow = middle ? SpecterRow.MIDDLE : (pos.y() < 99 ? SpecterRow.NORTH : SpecterRow.SOUTH);
					if (count == 2 && handRow == circleRow) {
						s.updateCall(circleOfLivesDodge, e1);
					}
					else if (count == 3 && handRow == circleRow) {
						s.updateCall(circleOfLivesWait, e1);
					}
					else {
						s.updateCall(circleOfLives, e1);
					}
					return;
				}
				// 6回目と8回目は手が発動しない
				if (count == 6 || count == 8) {
					s.updateCall(circleOfLives, e1);
					return;
				}
				if (rows.isEmpty() || rows.size() > 2) {
					log.error("Circle of Lives: invalid rows {}", rows);
					return;
				}
				ArenaSector towards;
				if (rows.get(0) == SpecterRow.MIDDLE) {
					towards = pos.y() < 100 ? ArenaSector.NORTH : ArenaSector.SOUTH;
				}
				else {
					towards = ArenaSector.CENTER;
				}
				loomingSpecters.clear();
				s.setParam("to", towards);
				s.updateCall(circleOfLivesLean, e1);
			});

	@NpcCastCallout(0xAE33)
	private final ModifiableCallout<AbilityCastStart> massMacabre = ModifiableCallout.durationBasedCall("Mass Macabre", "Preposition for Towers");

	private final ModifiableCallout<AbilityUsedEvent> massMacabreNext = new ModifiableCallout<>("Mass Macabre: Next Tower", "Soak Next Tower");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> massMacabreSq = SqtTemplates.sq(5_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0xAF13) && aue.getTarget().isThePlayer(),
			(e1, s) -> {
				macabreTowerCount++;
				if (macabreTowerCount >= 4) {
					return;
				}
				// タンクは2本目以降は強攻撃の処理に回る
				if (state.playerJobMatches(Job::isTank) && macabreTowerCount > 1) {
					return;
				}
				s.waitMs(4_000);
				s.updateCall(massMacabreNext, e1);
			});

}
