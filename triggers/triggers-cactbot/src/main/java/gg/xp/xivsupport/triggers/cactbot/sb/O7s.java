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
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.support.PlayerStatusCallout;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/raid/o7s.ts をもとに移植
@CalloutRepo(name = "Sigmascape V30 Savage", duty = KnownDuty.O3S)
public class O7s extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public O7s(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.O3S);
	}

	// 最初にどちらのシミュレーションが来たかで、以降のロード/ランの順番が決まる
	private boolean seenVirus;
	private boolean firstSet;
	private boolean firstBiblio;
	private int loadCount;
	private int runCount;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		seenVirus = false;
		firstSet = false;
		firstBiblio = false;
		loadCount = 0;
		runCount = 0;
	}

	@HandleEvents
	public void simulationTracker(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(0x5D5)) {
			seenVirus = true;
			return;
		}
		if (!event.buffIdMatches(0x5D3, 0x5D4)) {
			return;
		}
		// ウイルス前に来たものだけが順番の判定に使われる
		if (!firstSet && !seenVirus) {
			firstSet = true;
			firstBiblio = event.buffIdMatches(0x5D4);
		}
	}

	@NpcCastCallout(0x2788)
	private final ModifiableCallout<AbilityCastStart> magitekRay = ModifiableCallout.durationBasedCall("Magitek Ray", "Magitek Ray");

	@NpcCastCallout(0x2789)
	private final ModifiableCallout<AbilityCastStart> armAndHammer = ModifiableCallout.durationBasedCall("Arm and Hammer", "Buster on {event.target}");

	@PlayerHeadmarker(0x17)
	private final ModifiableCallout<HeadMarkerEvent> orbMarker = new ModifiableCallout<>("Orb Marker", "Orb Marker");

	private final ModifiableCallout<HeadMarkerEvent> blueMarkerOnYou = new ModifiableCallout<>("Blue Marker (On You)", "Blue Marker on You");
	private final ModifiableCallout<HeadMarkerEvent> blueMarker = new ModifiableCallout<>("Blue Marker", "Blue Marker on {event.target}");
	private final ModifiableCallout<HeadMarkerEvent> preyOnYou = new ModifiableCallout<>("Prey (On You)", "Prey on You");
	private final ModifiableCallout<HeadMarkerEvent> prey = new ModifiableCallout<>("Prey", "Prey on {event.target}");

	@HandleEvents
	public void markers(EventContext context, HeadMarkerEvent event) {
		boolean onYou = event.getTarget().isThePlayer();
		if (event.markerIdMatches(0x0E)) {
			context.accept((onYou ? blueMarkerOnYou : blueMarker).getModified(event));
		}
		else if (event.markerIdMatches(0x1E)) {
			context.accept((onYou ? preyOnYou : prey).getModified(event));
		}
	}

	@PlayerStatusCallout(0x178)
	private final ModifiableCallout<BuffApplied> searingWind = new ModifiableCallout<BuffApplied>("Searing Wind", "Out").statusIcon(0x178);

	@PlayerStatusCallout(0x58A)
	private final ModifiableCallout<BuffApplied> abandonment = new ModifiableCallout<BuffApplied>("Abandonment", "Abandonment: Stay Middle").statusIcon(0x58A);

	private final ModifiableCallout<BuffApplied> rotOnYou = new ModifiableCallout<BuffApplied>("Aether Rot (On You)", "Rot on You").statusIcon(0x5C3);
	private final ModifiableCallout<BuffApplied> rot = new ModifiableCallout<BuffApplied>("Aether Rot", "Rot on {event.target}").statusIcon(0x5C3);

	@HandleEvents
	public void aetherRot(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(0x5C3)) {
			context.accept((event.getTarget().isThePlayer() ? rotOnYou : rot).getModified(event));
		}
	}

	@NpcCastCallout(0x2AB5)
	private final ModifiableCallout<AbilityCastStart> stoneskin = ModifiableCallout.durationBasedCall("Stoneskin", "Interrupt");

	private final ModifiableCallout<AbilityCastStart> loadScreen = ModifiableCallout.durationBasedCall("Load (Unknown)", "Biblio or Knockback");
	private final ModifiableCallout<AbilityCastStart> loadBiblio = ModifiableCallout.durationBasedCall("Load (Bibliotaph)", "Biblio: Positions");
	private final ModifiableCallout<AbilityCastStart> loadDada = ModifiableCallout.durationBasedCall("Load (Dadaluma)", "Dada: Knockback");
	private final ModifiableCallout<AbilityCastStart> loadShips = ModifiableCallout.durationBasedCall("Load (Air Force)", "Ships: Out of Melee");
	private final ModifiableCallout<AbilityCastStart> loadUltros = ModifiableCallout.durationBasedCall("Load (Ultros)", "Ultros: Ink Spread");
	private final ModifiableCallout<AbilityCastStart> loadVirus = ModifiableCallout.durationBasedCall("Load (Virus)", "Virus");

	// ロード・スキップ・リトリーブ・ペーストはどれも次に来るものの予告として扱う
	@HandleEvents
	public void load(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x275C, 0x2773, 0x2774, 0x2776)) {
			return;
		}
		loadCount++;
		ModifiableCallout<AbilityCastStart> call = switch (loadCount) {
			case 1, 7 -> loadScreen;
			case 2, 8 -> firstBiblio ? loadDada : loadBiblio;
			case 3, 6 -> firstBiblio ? loadUltros : loadShips;
			case 4, 9 -> firstBiblio ? loadShips : loadUltros;
			case 5 -> loadVirus;
			default -> null;
		};
		if (call != null) {
			context.accept(call.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> runBiblio = ModifiableCallout.durationBasedCall("Run (Bibliotaph)", "Biblio Add");
	private final ModifiableCallout<AbilityCastStart> runDada = ModifiableCallout.durationBasedCall("Run (Dadaluma)", "Dada Add");
	private final ModifiableCallout<AbilityCastStart> runShips = ModifiableCallout.durationBasedCall("Run (Air Force)", "Ship Add");
	private final ModifiableCallout<AbilityCastStart> runUltros = ModifiableCallout.durationBasedCall("Run (Ultros)", "Ultros Add");

	@HandleEvents
	public void runCast(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x276F)) {
			return;
		}
		runCount++;
		ModifiableCallout<AbilityCastStart> call = switch (runCount) {
			case 1 -> runDada;
			case 2, 4 -> firstBiblio ? runUltros : runShips;
			case 3, 6 -> firstBiblio ? runShips : runUltros;
			case 5 -> runBiblio;
			default -> null;
		};
		if (call != null) {
			context.accept(call.getModified(event));
		}
	}

}
