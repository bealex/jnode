package jnode.ftn.tosser;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.j256.ormlite.dao.GenericRawResults;
import com.j256.ormlite.field.DataType;
import com.j256.ormlite.stmt.UpdateBuilder;

import jnode.dto.Dupe;
import jnode.dto.Echoarea;
import jnode.dto.Echomail;
import jnode.dto.Link;
import jnode.dto.LinkOption;
import jnode.dto.Netmail;
import jnode.dto.Readsign;
import jnode.dto.Subscription;
import jnode.ftn.FtnTools;
import jnode.ftn.types.Ftn2D;
import jnode.ftn.types.FtnAddress;
import jnode.ftn.types.FtnMessage;
import jnode.ftn.types.FtnPkt;
import jnode.logger.Logger;
import jnode.main.Main;
import jnode.main.threads.PollQueue;
import jnode.ndl.FtnNdlAddress;
import jnode.ndl.FtnNdlAddress.Status;
import jnode.ndl.NodelistScanner;
import jnode.orm.ORMManager;
import jnode.protocol.io.Message;

/**
 * 
 * @author kreon
 * 
 */
public class FtnTosser {
	private static final Logger logger = Logger.getLogger(FtnTosser.class);
	private Map<String, Integer> tossed = new HashMap<String, Integer>();
	private Map<String, Integer> bad = new HashMap<String, Integer>();
	private Set<Echoarea> affectedAreas = new HashSet<Echoarea>();

	/**
	 * Разбор нетмейла
	 * 
	 * @param netmail
	 * @param secure
	 */
	private void tossNetmail(FtnMessage netmail, boolean secure) {
		boolean drop = false;
		if (secure) {
			if (FtnTools.checkRobot(netmail)) {
				return;
			}
		}
		FtnNdlAddress from = NodelistScanner.getInstance().isExists(
				netmail.getFromAddr());
		FtnNdlAddress to = NodelistScanner.getInstance().isExists(
				netmail.getToAddr());

		if (from == null) {
			if (!secure) {
				logger.warn(String
						.format("Netmail %s -> %s уничтожен ( отправитель не найден в нодлисте )",
								netmail.getFromAddr().toString(), netmail
										.getToAddr().toString()));
				drop = true;
			} else {
				logger.warn(String
						.format("Netmail %s -> %s warn ( отправитель не найден в нодлисте )",
								netmail.getFromAddr().toString(), netmail
										.getToAddr().toString()));
			}

		} else if (to == null) {
			FtnTools.writeReply(
					netmail,
					"Destination not found",
					"Sorry, but destination of your netmail is not found in nodelist\nMessage rejected");
			logger.warn(String
					.format("Netmail %s -> %s уничтожен ( получатель не найден в нодлисте )",
							netmail.getFromAddr().toString(), netmail
									.getToAddr().toString()));

			drop = true;
		} else if (to.getStatus().equals(Status.DOWN)) {
			FtnTools.writeReply(netmail, "Destination is DOWN",
					"Warning! Destination of your netmail is DOWN");
			logger.warn(String.format(
					"Netmail %s -> %s warn ( получатель имеет статус Down )",
					netmail.getFromAddr().toString(), netmail.getToAddr()
							.toString()));
			drop = true;
		} else if (to.getStatus().equals(Status.HOLD)) {
			FtnTools.writeReply(netmail, "Destination is HOLD",
					"Warning! Destination of your netmail is HOLD");
			logger.warn(String.format(
					"Netmail %s -> %s warn ( получатель имеет статус Hold )",
					netmail.getFromAddr().toString(), netmail.getToAddr()
							.toString()));
		}

		if (drop) {
			Integer n = bad.get("netmail");
			bad.put("netmail", (n == null) ? 1 : n + 1);
		} else {
			if ((netmail.getAttribute() & FtnMessage.ATTR_ARQ) > 0) {
				FtnTools.writeReply(netmail, "ARQ reply",
						"Your message was successfully reached this system");
			}
			FtnTools.processRewrite(netmail);
			Link routeVia = FtnTools.getRouting(netmail);

			Netmail dbnm = new Netmail();
			dbnm.setRouteVia(routeVia);
			dbnm.setDate(netmail.getDate());
			dbnm.setFromFTN(netmail.getFromAddr().toString());
			dbnm.setToFTN(netmail.getToAddr().toString());
			dbnm.setFromName(netmail.getFromName());
			dbnm.setToName(netmail.getToName());
			dbnm.setSubject(netmail.getSubject());
			dbnm.setText(netmail.getText());
			dbnm.setAttr(netmail.getAttribute());
			try {
				ORMManager.INSTANSE.netmail().create(dbnm);
				Integer n = tossed.get("netmail");
				tossed.put("netmail", (n == null) ? 1 : n + 1);
				if (routeVia == null) {
					logger.warn(String
							.format("Netmail %s -> %s не будет отправлен ( не найден роутинг )",
									netmail.getFromAddr().toString(), netmail
											.getToAddr().toString()));
				} else {
					routeVia = ORMManager.INSTANSE.link().queryForSameId(
							routeVia);
					logger.debug(String.format(
							"Netmail %s -> %s будет отправлен через %s",
							netmail.getFromAddr().toString(), netmail
									.getToAddr().toString(), routeVia
									.getLinkAddress()));
					if (FtnTools.getOptionBooleanDefTrue(routeVia,
							LinkOption.BOOLEAN_CRASH_NETMAIL)) {
						PollQueue.INSTANSE.add(routeVia);
					}
				}
			} catch (SQLException e) {
				logger.error("Ошибка при сохранении нетмейла", e);
			}
		}
	}

	private void tossEchomail(FtnMessage echomail, Link link, boolean secure) {

		if (!secure) {
			logger.warn("Эхомейл по unsecure-соединению - уничтожен");
			return;
		}
		Echoarea area = FtnTools.getAreaByName(echomail.getArea(), link);
		if (area == null) {
			logger.warn("Эхоконференция " + echomail.getArea()
					+ " недоступна для узла " + link.getLinkAddress());
			Integer n = bad.get(echomail.getArea());
			bad.put(echomail.getArea(), (n == null) ? 1 : n + 1);
			return;
		}
		if (FtnTools.isADupe(area, echomail.getMsgid())) {
			logger.warn("Сообщение " + echomail.getArea() + " "
					+ echomail.getMsgid() + " является дюпом");
			Integer n = bad.get(echomail.getArea());
			bad.put(echomail.getArea(), (n == null) ? 1 : n + 1);
			return;
		}

		FtnTools.processRewrite(echomail);

		Echomail mail = new Echomail();
		mail.setArea(area);
		mail.setDate(echomail.getDate());
		mail.setFromFTN(echomail.getFromAddr().toString());
		mail.setFromName(echomail.getFromName());
		mail.setToName(echomail.getToName());
		mail.setSubject(echomail.getSubject());
		mail.setText(echomail.getText());
		mail.setSeenBy(FtnTools.write2D(echomail.getSeenby(), true));
		mail.setPath(FtnTools.write2D(echomail.getPath(), false));
		try {
			ORMManager.INSTANSE.echomail().create(mail);
		} catch (SQLException e) {
			logger.warn("Не удалось сохранить сообщение", e);
			Integer n = bad.get(echomail.getArea());
			bad.put(echomail.getArea(), (n == null) ? 1 : n + 1);
			return;
		}
		affectedAreas.add(area);
		{
			if (link != null) {
				Readsign sign = new Readsign();
				sign.setLink(link);
				sign.setMail(mail);
				try {

					ORMManager.INSTANSE.readsign().create(sign);
				} catch (SQLException e) {
				}
			}
			Dupe dupe = new Dupe();
			dupe.setEchoarea(area);
			dupe.setMsgid(echomail.getMsgid());
			try {
				ORMManager.INSTANSE.dupe().create(dupe);
			} catch (SQLException e1) {
			}

		}

		Integer n = tossed.get(echomail.getArea());
		tossed.put(echomail.getArea(), (n == null) ? 1 : n + 1);

	}

	/**
	 * Получаем сообщения из бандлов
	 * 
	 * @param connector
	 */
	public void tossIncoming(Message message, Link link) {
		if (message == null) {
			return;
		}
		FtnPkt[] pkts = FtnTools.unpack(message);

		for (FtnPkt pkt : pkts) {
			if (message.isSecure()) {
				if (!FtnTools.getOptionBooleanDefFalse(link,
						LinkOption.BOOLEAN_IGNORE_PKTPWD)) {
					if (!link.getPaketPassword().equalsIgnoreCase(
							pkt.getPassword())) {
						logger.warn("Пароль для пакета не совпал - пакет перемещен в inbound");
						FtnTools.moveToBad(pkt);
						continue;
					}
				}
			}
			for (FtnMessage ftnm : pkt.getMessages()) {
				if (message.isSecure()) {
					if (FtnTools.checkRobot(ftnm)) {
						continue;
					}
				}
				if (ftnm.isNetmail()) {
					tossNetmail(ftnm, message.isSecure());
				} else {
					tossEchomail(ftnm, link, message.isSecure());
				}
			}

		}
	}

	public void tossInbound() {
		File inbound = new File(Main.getInbound());
		for (File file : inbound.listFiles()) {
			if (file.getName().matches("^[a-f0-9]{8}\\.pkt$")) {
				try {
					Message m = new Message(file);
					logger.debug("Обрабатываем файл " + file.getAbsolutePath());
					FtnPkt[] pkts = FtnTools.unpack(m);
					for (FtnPkt pkt : pkts) {
						for (FtnMessage ftnm : pkt.getMessages()) {
							if (ftnm.isNetmail()) {
								tossNetmail(ftnm, true);
							} else {
								tossEchomail(ftnm, null, true);
							}
						}
					}
					file.delete();
				} catch (Exception e) {
					logger.warn("Не могу обработать пакет "
							+ file.getAbsolutePath());
				}
			}
		}
	}

	public void end() {
		if (!tossed.isEmpty()) {
			logger.info("Записано сообщений:");
			for (String area : tossed.keySet()) {
				logger.info(String.format("\t%s - %d", area, tossed.get(area)));
			}
		}
		if (!bad.isEmpty()) {
			logger.warn("Уничтожено сообщений:");
			for (String area : bad.keySet()) {
				logger.warn(String.format("\t%s - %d", area, bad.get(area)));
			}
		}
		for (Echoarea areas : affectedAreas) {
			for (Link l : FtnTools.getSubscribers(areas)) {
				if (FtnTools.getOptionBooleanDefFalse(l,
						LinkOption.BOOLEAN_CRASH_ECHOMAIL)) {
					PollQueue.INSTANSE.add(l);
				}
			}
		}
	}

	/**
	 * Получить новые сообщения для линка
	 * 
	 * @param link
	 * @return
	 */
	public static List<Message> getMessagesForLink(Link link) {
		FtnAddress link_address = new FtnAddress(link.getLinkAddress());
		FtnAddress our_address = Main.info.getAddress();
		Ftn2D link2d = new Ftn2D(link_address.getNet(), link_address.getNode());
		Ftn2D our2d = new Ftn2D(our_address.getNet(), our_address.getNode());
		List<FtnMessage> messages = new ArrayList<FtnMessage>();
		List<File> attachedFiles = new ArrayList<File>();
		try {
			List<Netmail> netmails = ORMManager.INSTANSE.netmail()
					.queryBuilder().where().eq("send", false).and()
					.eq("route_via", link).query();
			if (!netmails.isEmpty()) {
				for (Netmail netmail : netmails) {
					FtnMessage msg = FtnTools.netmailToFtnMessage(netmail);
					messages.add(msg);
					logger.debug(String.format(
							"Пакуем netmail #%d %s -> %s для %s (%d)",
							netmail.getId(), netmail.getFromFTN(),
							netmail.getToFTN(), link.getLinkAddress(),
							msg.getAttribute()));
					if ((netmail.getAttr() & FtnMessage.ATTR_FILEATT) > 0) {
						String filename = netmail.getSubject();
						filename = filename.replaceAll("^[\\./\\\\]+", "_");
						File file = new File(Main.getInbound() + File.separator
								+ filename);
						if (file.canRead()) {
							attachedFiles.add(file);
							logger.debug("К сообщению прикреплен файл "
									+ filename + ", пересылаем");
						}
					}
					netmail.setSend(true);
					ORMManager.INSTANSE.netmail().update(netmail);
				}
			}
		} catch (Exception e) {
			logger.error(
					"Ошибка обработки netmail для " + link.getLinkAddress(), e);
		}
		try {
			final String echomail_query = "SELECT a.name as AREA,e.* FROM subscription s LEFT JOIN echoarea"
					+ " a ON (a.id=s.echoarea_id) LEFT JOIN echomail e ON"
					+ " (e.echoarea_id=s.echoarea_id) WHERE e.id > s.lastmessageid AND"
					+ " e.id NOT IN (SELECT r.echomail_id FROM readsing r WHERE"
					+ " r.link_id=s.link_id AND r.echomail_id > s.lastmessageid) AND"
					+ " s.link_id=%d ORDER BY e.id ASC LIMIT 100";
			final String seenby_query = "SELECT l.ftn_address from subscription s left join links l on (l.id=s.link_id) where s.echoarea_id=%d";
			DataType[] types = new DataType[] { DataType.STRING, // area [0]
					DataType.LONG_OBJ, // id [1]
					DataType.LONG_OBJ, // earea_id [2]
					DataType.STRING, // from name [3]
					DataType.STRING, // to name [4]
					DataType.STRING, // from addr [5]
					DataType.DATE_LONG, // date [6]
					DataType.LONG_STRING, // subject [7]
					DataType.LONG_STRING, // message [8]
					DataType.LONG_STRING, // seenby [9]
					DataType.LONG_STRING // path [10]
			};
			Map<Long, Long> subcription = new HashMap<Long, Long>();
			List<Long> signs = new ArrayList<Long>();
			GenericRawResults<Object[]> results = ORMManager.INSTANSE
					.echomail().queryRaw(
							String.format(echomail_query, link.getId()), types);
			if (results.getNumberColumns() > 0) {
				for (Object[] result : results.getResults()) {
					Set<Ftn2D> seenby = new HashSet<Ftn2D>(
							FtnTools.read2D((String) result[9]));
					subcription.put((Long) result[2], (Long) result[1]);
					if (seenby.contains(link2d) && link_address.getPoint() == 0) {
						logger.debug(link2d + " есть в синбаях для "
								+ link_address);
					} else {
						signs.add((Long) result[1]);
						seenby.add(our2d);
						seenby.add(link2d);
						List<Ftn2D> path = FtnTools.read2D((String) result[10]);
						if (!path.contains(our2d)) {
							path.add(our2d);
						}
						GenericRawResults<String[]> seens = ORMManager.INSTANSE
								.link().queryRaw(
										String.format(seenby_query,
												(Long) result[2]));
						for (String[] seen : seens.getResults()) {
							FtnAddress addr = new FtnAddress(seen[0]);
							Ftn2D d2 = new Ftn2D(addr.getNet(), addr.getNode());
							seenby.add(d2);
						}
						FtnMessage message = new FtnMessage();
						message.setNetmail(false);
						message.setArea(((String) result[0]).toUpperCase());
						message.setFromName((String) result[3]);
						message.setToName((String) result[4]);
						message.setFromAddr(Main.info.getAddress());
						message.setToAddr(link_address);
						message.setDate((Date) result[6]);
						message.setSubject((String) result[7]);
						message.setText((String) result[8]);
						message.setSeenby(new ArrayList<Ftn2D>(seenby));
						message.setPath(path);
						logger.debug("Пакуем сообщение #" + result[1] + " ("
								+ result[0] + ") для " + link.getLinkAddress());
						messages.add(message);
					}
				}
				for (Long id : signs) {
					Echomail m = new Echomail();
					m.setId(id);
					ORMManager.INSTANSE.readsign()
							.create(new Readsign(link, m));

				}
				for (Long echoid : subcription.keySet()) {
					UpdateBuilder<Subscription, ?> upd = ORMManager.INSTANSE
							.subscription().updateBuilder();
					upd.updateColumnValue("lastmessageid",
							subcription.get(echoid));
					upd.where().eq("link_id", link).and()
							.eq("echoarea_id", echoid);
					ORMManager.INSTANSE.subscription().update(upd.prepare());
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			logger.error(
					"Ошибка обработки echomail для " + link.getLinkAddress(), e);
		}
		if (!messages.isEmpty()) {
			List<Message> ret = FtnTools.pack(messages, link);
			for (File f : attachedFiles) {
				try {
					ret.add(new Message(f));
					f.delete();
				} catch (Exception e) {
					logger.warn("Не могу прикрепить файл "
							+ f.getAbsolutePath());
				}
			}
			return ret;
		} else {
			return new ArrayList<Message>();
		}
	}
}